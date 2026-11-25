package com.example.lms.service;

import com.example.lms.dto.BulkEnrollmentDto;
import com.example.lms.dto.UserImportDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.PhoneValidationUtil;
import com.example.lms.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bulk enrollment from the All Users page (Bulk Users menu).
 *
 * 1) Enroll in course (Excel upload): one row = one (user, course) pair.
 *    - the email is already a learner of this company  -> only the enrollment is added;
 *    - the email is not registered at all               -> the learner is created (same rules as
 *      "Import users": random unusable password + "set your password" mail) and then enrolled;
 *    - anything else (other company, instructor/admin, inactive, deleted) -> the row is rejected.
 *    Valid rows are saved even when other rows fail; the response lists every rejected row.
 *    A user listed on several rows (several courses) is created once.
 *
 * 2) Unenroll from course: the ticked users of the All Users table.
 *    Every user is judged on his own: the valid ones are unenrolled, the rest come back in
 *    {@code skipped} with a reason.
 *
 * Everything is scoped to the caller's company (course AND users).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkEnrollmentService {

    static final int MAX_USERS = 200;
    public static final int MAX_ROWS = 500;

    private static final List<String> HEADERS = List.of(
            "First Name", "Last Name", "Email", "Country Code", "Phone Number", "Department", "Course Name");
    private static final int COL_FIRST = 0, COL_LAST = 1, COL_EMAIL = 2, COL_CODE = 3,
            COL_PHONE = 4, COL_DEPT = 5, COL_COURSE = 6;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CourseAccessService courseAccess;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserImportService userImportService; // re-used for the "set your password" mail

    private static final List<String> UNENROLL_HEADERS = List.of("Email", "Course Name");

    /** One data row of the unenroll sheet. */
    private record UnenrollLine(int row, String email, String course) {}

    /** One data row of the Excel sheet. */
    private record Line(int row, String first, String last, String email,
                        String code, String phone, String dept, String course) {}

    @Transactional(readOnly = true)
    public List<BulkEnrollmentDto.CourseOption> listCourses() {
        return courseRepository.findByOrganizationIdOrderByCreatedAtDesc(courseAccess.currentOrganizationId())
                .stream()
                .filter(c -> !"ARCHIVED".equalsIgnoreCase(c.getStatus()))
                .map(c -> BulkEnrollmentDto.CourseOption.builder()
                        .id(c.getId()).name(c.getName()).status(c.getStatus()).build())
                .toList();
    }

    @Transactional
    public BulkEnrollmentDto.Result unenroll(Long courseId, List<Long> rawIds) {
        CourseEntity course = courseAccess.requireCourseInOrganization(courseId);
        Set<Long> ids = normalize(rawIds);
        Map<Long, User> users = companyUsers(ids);
        Set<Long> enrolled = new HashSet<>(enrollmentRepository.findEnrolledUserIds(courseId, ids));

        List<BulkEnrollmentDto.Skipped> skipped = new ArrayList<>();
        Set<Long> toDelete = new LinkedHashSet<>();
        for (Long id : ids) {
            User u = users.get(id);
            if (u == null) skipped.add(skip(id, null, "User not found."));
            else if (!enrolled.contains(id)) skipped.add(skip(id, u, "Not enrolled in this course."));
            else toDelete.add(id);
        }

        int deleted = toDelete.isEmpty() ? 0 : enrollmentRepository.deleteByCourseIdAndUserIdIn(courseId, toDelete);
        return result(course, ids.size(), deleted, skipped);
    }

    /* ---------------------------------------------------------------- */

    private Map<Long, User> companyUsers(Set<Long> ids) {
        return userRepository
                .findByIdInAndCompanyAdminIdAndDeletedAtIsNull(ids, courseAccess.currentOrganizationId())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Set<Long> normalize(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException("Please select at least one user", HttpStatus.BAD_REQUEST);
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id == null || id <= 0) throw new ApiException("User is required.", HttpStatus.BAD_REQUEST);
            ids.add(id);
        }
        if (ids.size() > MAX_USERS) {
            throw new ApiException("You can select at most " + MAX_USERS + " users at a time.", HttpStatus.BAD_REQUEST);
        }
        return ids;
    }

    private static BulkEnrollmentDto.Skipped skip(Long id, User u, String reason) {
        String name = u == null ? null : ((u.getFirstName() + " " + u.getLastName()).trim());
        return BulkEnrollmentDto.Skipped.builder().userId(id).name(name).reason(reason).build();
    }

    private static BulkEnrollmentDto.Result result(CourseEntity c, int requested, int affected,
                                                   List<BulkEnrollmentDto.Skipped> skipped) {
        return BulkEnrollmentDto.Result.builder()
                .courseId(c.getId()).courseName(c.getName())
                .requested(requested).affected(affected).skipped(skipped).build();
    }

    /* ------------------------------------------------------------------ */
    /*  Import                                                            */
    /* ------------------------------------------------------------------ */

    public BulkEnrollmentDto.ImportResult importAndEnroll(MultipartFile file) {
        Long ownerId = courseAccess.currentOrganizationId();

        if (file == null || file.isEmpty()) {
            throw new ApiException("Please choose an Excel file.", HttpStatus.BAD_REQUEST);
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            throw new ApiException("Only .xlsx files are supported. Download the sample template.", HttpStatus.BAD_REQUEST);
        }

        List<Line> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new ApiException("No rows found in the file.", HttpStatus.BAD_REQUEST);
        }

        // Courses of THIS company (not archived), by lower-cased name.
        Map<String, CourseEntity> courses = new HashMap<>();
        for (CourseEntity c : courseRepository.findByOrganizationIdOrderByCreatedAtDesc(ownerId)) {
            if (!"ARCHIVED".equalsIgnoreCase(c.getStatus()) && c.getName() != null) {
                courses.put(c.getName().trim().toLowerCase(), c);
            }
        }
        // Learner role of THIS company (needed only when a new user has to be created).
        RoleEntity learnerRole = roleRepository.findAllByCreatedByAdminIdAndStatus(ownerId, "Active").stream()
                .filter(r -> r.getName() != null && "learner".equalsIgnoreCase(r.getName().trim()))
                .findFirst().orElse(null);

        List<UserImportDto.RowError> errors = new ArrayList<>();
        Map<String, User> usable = new HashMap<>();          // email -> learner that can be enrolled
        Map<String, String> unusable = new HashMap<>();      // email -> why that user cannot be used
        Set<String> seen = new HashSet<>();                  // email|courseId already handled in this file
        int usersCreated = 0, enrolled = 0, alreadyEnrolled = 0, emailsFailed = 0;

        for (Line l : lines) {
            if (l.email().isEmpty() || !EMAIL.matcher(l.email()).matches() || l.email().length() > 100) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Enter a valid email address."));
                continue;
            }
            if (l.course().isEmpty()) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Course Name is required."));
                continue;
            }
            CourseEntity course = courses.get(l.course().toLowerCase());
            if (course == null) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(),
                        "Course '" + l.course() + "' was not found (check the name, archived courses cannot be used)."));
                continue;
            }
            if (!seen.add(l.email() + "|" + course.getId())) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Duplicate row inside the file."));
                continue;
            }

            User user = usable.get(l.email());
            if (user == null) {
                if (unusable.containsKey(l.email())) {
                    errors.add(new UserImportDto.RowError(l.row(), l.email(), unusable.get(l.email())));
                    continue;
                }
                Optional<User> existing = userRepository.findByEmailIgnoreCase(l.email());
                String problem;
                if (existing.isPresent()) {
                    user = existing.get();
                    problem = checkExisting(user, ownerId);
                } else {
                    problem = validateNewUser(l, learnerRole);
                    if (problem == null) {
                        try {
                            user = userRepository.save(User.builder()
                                    .firstName(l.first()).lastName(l.last()).email(l.email())
                                    .countryCode(normalizeCode(l.code())).phoneNumber(l.phone())
                                    .password(passwordEncoder.encode(TokenGenerator.generateRawToken())) // unusable until reset
                                    .role(learnerRole.getName()).roleId(learnerRole.getId())
                                    .companyAdminId(ownerId)
                                    .department(l.dept().isEmpty() ? null : l.dept())
                                    .acceptedTerms(true).active(true)
                                    .build());
                            usersCreated++;
                            if (!userImportService.sendSetPasswordMail(user)) emailsFailed++;
                        } catch (DataIntegrityViolationException e) {
                            problem = "This email is already registered.";
                        }
                    }
                }
                if (problem != null) {
                    unusable.put(l.email(), problem);
                    errors.add(new UserImportDto.RowError(l.row(), l.email(), problem));
                    continue;
                }
                usable.put(l.email(), user);
            }

            // Enroll (the unique (course, user) constraint is the last guard against races).
            if (enrollmentRepository.existsByCourseIdAndUserId(course.getId(), user.getId())) {
                alreadyEnrolled++;
                continue;
            }
            try {
                enrollmentRepository.save(EnrollmentEntity.builder()
                        .courseId(course.getId()).userId(user.getId()).enrolledAt(LocalDateTime.now())
                        .progressPercent(0).status(EnrollmentStatus.NOT_STARTED).build());
                enrolled++;
            } catch (DataIntegrityViolationException e) {
                alreadyEnrolled++;
            }
        }
        errors.sort(Comparator.comparingInt(UserImportDto.RowError::getRow));

        return BulkEnrollmentDto.ImportResult.builder()
                .total(lines.size()).usersCreated(usersCreated).enrolled(enrolled)
                .alreadyEnrolled(alreadyEnrolled).failed(errors.size())
                .emailsFailed(emailsFailed).errors(errors).build();
    }

    /** Existing account: only an active learner of the caller's company can be enrolled. */
    private static String checkExisting(User u, Long ownerId) {
        if (u.getDeletedAt() != null) return "This email belongs to a deleted account.";
        if (!Objects.equals(u.getCompanyAdminId(), ownerId)) return "This email is registered under another organization.";
        if (!"LEARNER".equalsIgnoreCase(u.getRole())) return "Only learners can be enrolled (role: " + u.getRole() + ").";
        if (!Boolean.TRUE.equals(u.getActive())) return "User is inactive.";
        return null;
    }

    /** Extra columns are mandatory only when the user has to be created. */
    private static String validateNewUser(Line l, RoleEntity learnerRole) {
        if (learnerRole == null) return "The Learner role is not available (create/activate it in Role Management).";
        if (l.first().isEmpty() || l.last().isEmpty()) return "New user: First Name and Last Name are required.";
        if (l.first().length() > 50 || l.last().length() > 50) return "Names can have at most 50 characters.";
        String code = normalizeCode(l.code());
        if (!PhoneValidationUtil.isSupportedCountryCode(code)) return "New user: Country Code is not supported (e.g. +91).";
        if (!l.phone().matches("\\d+") || !PhoneValidationUtil.isValid(code, l.phone())) {
            return "New user: Phone number length is not valid for " + code + ".";
        }
        if (l.dept().length() > 100) return "Department can have at most 100 characters.";
        return null;
    }

    private static String normalizeCode(String code) {
        return !code.isEmpty() && !code.startsWith("+") ? "+" + code : code;
    }

    /* ------------------------------------------------------------------ */
    /*  Reading the sheet                                                 */
    /* ------------------------------------------------------------------ */

    private List<Line> readLines(MultipartFile file) {
        List<Line> lines = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            validateHeader(sheet.getRow(0), fmt);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, fmt)) continue;
                if (lines.size() >= MAX_ROWS) {
                    throw new ApiException("A file can contain at most " + MAX_ROWS + " rows.", HttpStatus.BAD_REQUEST);
                }
                lines.add(new Line(i + 1,
                        cell(row, COL_FIRST, fmt), cell(row, COL_LAST, fmt),
                        cell(row, COL_EMAIL, fmt).toLowerCase(),
                        cell(row, COL_CODE, fmt), cell(row, COL_PHONE, fmt).replaceAll("[\\s-]", ""),
                        cell(row, COL_DEPT, fmt), cell(row, COL_COURSE, fmt)));
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Enrollment import: unreadable workbook: {}", e.getMessage());
            throw new ApiException("The file could not be read. Please use the sample template (.xlsx).", HttpStatus.BAD_REQUEST);
        }
        return lines;
    }

    private void validateHeader(Row header, DataFormatter fmt) {
        validateHeader(header, fmt, HEADERS);
    }

    private void validateHeader(Row header, DataFormatter fmt, List<String> expected) {
        if (header == null) throw new ApiException("The file is empty.", HttpStatus.BAD_REQUEST);
        for (int c = 0; c < expected.size(); c++) {
            String got = cell(header, c, fmt).replace("*", "").trim();
            if (!expected.get(c).equalsIgnoreCase(got)) {
                throw new ApiException("Header row does not match the sample template (expected '"
                        + expected.get(c) + "' in column " + (c + 1) + ").", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static boolean isBlank(Row row, DataFormatter fmt) {
        return isBlank(row, fmt, HEADERS.size());
    }

    private static boolean isBlank(Row row, DataFormatter fmt, int columns) {
        for (int c = 0; c < columns; c++) if (!cell(row, c, fmt).isEmpty()) return false;
        return true;
    }

    private static String cell(Row row, int col, DataFormatter fmt) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    /* ------------------------------------------------------------------ */
    /*  Sample template                                                   */
    /* ------------------------------------------------------------------ */

    public byte[] buildTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Enrollments");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@")); // keep +91 / leading zeros

            // Only Email and Course Name are always required.
            Set<Integer> starred = Set.of(COL_EMAIL, COL_COURSE);
            Row h = sheet.createRow(0);
            for (int c = 0; c < HEADERS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(HEADERS.get(c) + (starred.contains(c) ? "*" : ""));
                cell.setCellStyle(head);
                sheet.setDefaultColumnStyle(c, text);
                sheet.setColumnWidth(c, 5500);
            }
            String[][] sample = {
                    {"Asha", "Kumar", "asha.kumar@example.com", "+91", "9876543210", "Cyber security", "Intro to Cyber Security"},
                    {"", "", "ravi.existing@example.com", "", "", "", "Data Science Basics"}};
            for (int r = 0; r < sample.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sample[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(text);
                    cell.setCellValue(sample[r][c]);
                }
            }

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Fill the 'Enrollments' sheet. One row = one user + one course. Delete the two sample rows first.",
                    "Email* and Course Name* are always required. Course Name must match an existing course exactly (archived courses are not allowed).",
                    "Email already registered as a learner of your company: the user is only enrolled in the course.",
                    "Email not registered yet: the learner is created and enrolled. First Name, Last Name, Country Code and Phone Number are then required.",
                    "Country Code: with +, e.g. +91. Phone Number: digits only, without the country code. Department is optional.",
                    "To enroll one user in several courses, add one row per course (repeat the email).",
                    "New users receive an email to set their own password.",
                    "Maximum " + MAX_ROWS + " rows per file. Do not rename or reorder the header row."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 16000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Unable to create the template.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Unenroll (Excel upload)                                           */
    /* ------------------------------------------------------------------ */

    /**
     * One row = one (email, course) pair. The enrollment is removed only when the email belongs to a
     * (non-deleted) user of this company AND that user is enrolled in that course; every other row
     * is rejected with the exact reason. Valid rows are processed even when other rows fail.
     * Nothing is ever created here.
     */
    @Transactional
    public BulkEnrollmentDto.UnenrollImportResult importAndUnenroll(MultipartFile file) {
        Long ownerId = courseAccess.currentOrganizationId();
        requireXlsx(file);

        List<UnenrollLine> lines = readUnenrollLines(file);
        if (lines.isEmpty()) {
            throw new ApiException("No rows found in the file.", HttpStatus.BAD_REQUEST);
        }

        Map<String, CourseEntity> courses = new HashMap<>();
        for (CourseEntity c : courseRepository.findByOrganizationIdOrderByCreatedAtDesc(ownerId)) {
            if (!"ARCHIVED".equalsIgnoreCase(c.getStatus()) && c.getName() != null) {
                courses.put(c.getName().trim().toLowerCase(), c);
            }
        }

        List<UserImportDto.RowError> errors = new ArrayList<>();
        Map<String, Optional<User>> users = new HashMap<>();   // email -> user of this company
        Set<String> seen = new HashSet<>();                    // email|courseId already handled
        int unenrolled = 0;

        for (UnenrollLine l : lines) {
            if (l.email().isEmpty() || !EMAIL.matcher(l.email()).matches() || l.email().length() > 100) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Enter a valid email address."));
                continue;
            }
            if (l.course().isEmpty()) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Course Name is required."));
                continue;
            }
            CourseEntity course = courses.get(l.course().toLowerCase());
            if (course == null) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(),
                        "Course '" + l.course() + "' was not found (check the name, archived courses cannot be used)."));
                continue;
            }
            Optional<User> found = users.computeIfAbsent(l.email(), e ->
                    userRepository.findByEmailIgnoreCase(e)
                            .filter(u -> u.getDeletedAt() == null && Objects.equals(u.getCompanyAdminId(), ownerId)));
            if (found.isEmpty()) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "No user with this email in your organization."));
                continue;
            }
            if (!seen.add(l.email() + "|" + course.getId())) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(), "Duplicate row inside the file."));
                continue;
            }
            Long userId = found.get().getId();
            if (!enrollmentRepository.existsByCourseIdAndUserId(course.getId(), userId)) {
                errors.add(new UserImportDto.RowError(l.row(), l.email(),
                        "User is not enrolled in '" + course.getName() + "'."));
                continue;
            }
            unenrolled += enrollmentRepository.deleteByCourseIdAndUserIdIn(course.getId(), Set.of(userId));
        }
        errors.sort(Comparator.comparingInt(UserImportDto.RowError::getRow));

        return BulkEnrollmentDto.UnenrollImportResult.builder()
                .total(lines.size()).unenrolled(unenrolled).failed(errors.size()).errors(errors).build();
    }

    private List<UnenrollLine> readUnenrollLines(MultipartFile file) {
        List<UnenrollLine> lines = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            validateHeader(sheet.getRow(0), fmt, UNENROLL_HEADERS);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, fmt, UNENROLL_HEADERS.size())) continue;
                if (lines.size() >= MAX_ROWS) {
                    throw new ApiException("A file can contain at most " + MAX_ROWS + " rows.", HttpStatus.BAD_REQUEST);
                }
                lines.add(new UnenrollLine(i + 1, cell(row, 0, fmt).toLowerCase(), cell(row, 1, fmt)));
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Unenroll import: unreadable workbook: {}", e.getMessage());
            throw new ApiException("The file could not be read. Please use the sample template (.xlsx).", HttpStatus.BAD_REQUEST);
        }
        return lines;
    }

    private static void requireXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Please choose an Excel file.", HttpStatus.BAD_REQUEST);
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            throw new ApiException("Only .xlsx files are supported. Download the sample template.", HttpStatus.BAD_REQUEST);
        }
    }

    public byte[] buildUnenrollTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Unenroll");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            Row h = sheet.createRow(0);
            for (int c = 0; c < UNENROLL_HEADERS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(UNENROLL_HEADERS.get(c) + "*");
                cell.setCellStyle(head);
                sheet.setDefaultColumnStyle(c, text);
                sheet.setColumnWidth(c, 8000);
            }
            Row r = sheet.createRow(1);
            String[] sample = {"asha.kumar@example.com", "Intro to Cyber Security"};
            for (int c = 0; c < sample.length; c++) {
                Cell cell = r.createCell(c);
                cell.setCellStyle(text);
                cell.setCellValue(sample[c]);
            }

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Fill the 'Unenroll' sheet. One row = one user + one course. Delete the sample row first.",
                    "Email* and Course Name* are required. Course Name must match an existing course exactly (archived courses are not allowed).",
                    "The user is unenrolled only if the email belongs to a user of your organization and that user is enrolled in the course.",
                    "Any other row is skipped and listed with the reason. Nothing is created or deleted apart from the enrollment.",
                    "To unenroll one user from several courses, add one row per course (repeat the email).",
                    "Maximum " + MAX_ROWS + " rows per file. Do not rename or reorder the header row."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 16000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Unable to create the template.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}