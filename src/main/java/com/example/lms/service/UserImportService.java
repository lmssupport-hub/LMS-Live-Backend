package com.example.lms.service;

import com.example.lms.dto.UserImportDto;
import com.example.lms.entity.PasswordResetToken;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.PasswordResetTokenRepository;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.PhoneValidationUtil;
import com.example.lms.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * "All Users" > Bulk Users > Import users.
 *
 * Admin downloads the sample .xlsx, fills it and uploads it. Every valid row becomes a row in the
 * {@code users} table (company = the caller's company, role = Learner / Instructor of that company).
 * The password is a random unusable one; each user gets a "set your password" mail that reuses the
 * forgot-password token table + /reset-password page.
 *
 * Valid rows are saved even when other rows fail; the response lists every rejected row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportService {

    public static final int MAX_ROWS = 500;

    private static final List<String> HEADERS = List.of(
            "First Name", "Last Name", "Email", "Country Code", "Phone Number", "Role", "Department");
    private static final Set<String> ALLOWED_ROLES = Set.of("learner", "instructor");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService emailService;

    @Value("${reset-link.base-url}")
    private String resetLinkBaseUrl;

    /** Longer than the 30 min forgot-password link: the user did not ask for this mail. */
    @Value("${app.import.set-password-expiry-minutes:4320}")
    private int setPasswordExpiryMinutes;

    /* ------------------------------------------------------------------ */
    /*  Import                                                            */
    /* ------------------------------------------------------------------ */

    public UserImportDto.Result importUsers(Long actorId, MultipartFile file) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
        Long ownerId = PackageService.resolveOwnerId(actor);

        if (file == null || file.isEmpty()) {
            throw new ApiException("Please choose an Excel file.", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw new ApiException("Only .xlsx files are supported. Download the sample template.", HttpStatus.BAD_REQUEST);
        }

        // Learner / Instructor roles of THIS company (active only), by lower-cased name.
        Map<String, RoleEntity> roles = new HashMap<>();
        for (RoleEntity r : roleRepository.findAllByCreatedByAdminIdAndStatus(ownerId, "Active")) {
            String key = r.getName().trim().toLowerCase();
            if (ALLOWED_ROLES.contains(key)) roles.put(key, r);
        }

        List<UserImportDto.RowError> errors = new ArrayList<>();
        List<User> toSave = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        DataFormatter fmt = new DataFormatter();
        int total = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            validateHeader(sheet.getRow(0), fmt);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, fmt)) continue;
                if (++total > MAX_ROWS) {
                    throw new ApiException("A file can contain at most " + MAX_ROWS + " users.", HttpStatus.BAD_REQUEST);
                }
                int excelRow = i + 1;
                String first = cell(row, 0, fmt), last = cell(row, 1, fmt);
                String email = cell(row, 2, fmt).toLowerCase();
                String code = cell(row, 3, fmt), phone = cell(row, 4, fmt).replaceAll("[\\s-]", "");
                String roleName = cell(row, 5, fmt), dept = cell(row, 6, fmt);
                if (!code.isEmpty() && !code.startsWith("+")) code = "+" + code;

                String problem = validate(first, last, email, code, phone, roleName, dept, roles, seenInFile);
                if (problem != null) {
                    errors.add(new UserImportDto.RowError(excelRow, email, problem));
                    continue;
                }
                RoleEntity role = roles.get(roleName.toLowerCase());
                toSave.add(User.builder()
                        .firstName(first).lastName(last).email(email)
                        .countryCode(code).phoneNumber(phone)
                        .password(passwordEncoder.encode(TokenGenerator.generateRawToken())) // unusable until reset
                        .role(role.getName()).roleId(role.getId())
                        .companyAdminId(ownerId)
                        .department(dept.isEmpty() ? null : dept)
                        .acceptedTerms(true).active(true)
                        .build());
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("User import: unreadable workbook: {}", e.getMessage());
            throw new ApiException("The file could not be read. Please use the sample template (.xlsx).", HttpStatus.BAD_REQUEST);
        }

        if (total == 0) {
            throw new ApiException("No users found in the file.", HttpStatus.BAD_REQUEST);
        }

        // Save row by row so one DB conflict (e.g. a race on the unique email) cannot sink the rest.
        int imported = 0, emailsFailed = 0;
        for (User u : toSave) {
            try {
                User saved = userRepository.save(u);
                imported++;
                if (!sendSetPasswordMail(saved)) emailsFailed++;
            } catch (DataIntegrityViolationException e) {
                errors.add(new UserImportDto.RowError(0, u.getEmail(), "This email is already registered."));
            }
        }
        errors.sort(Comparator.comparingInt(UserImportDto.RowError::getRow));

        return UserImportDto.Result.builder()
                .total(total).imported(imported).failed(errors.size())
                .emailsFailed(emailsFailed).errors(errors).build();
    }

    private String validate(String first, String last, String email, String code, String phone,
                            String roleName, String dept, Map<String, RoleEntity> roles, Set<String> seen) {
        if (first.isEmpty() || last.isEmpty()) return "First Name and Last Name are required.";
        if (first.length() > 50 || last.length() > 50) return "Names can have at most 50 characters.";
        if (email.isEmpty() || !EMAIL.matcher(email).matches() || email.length() > 100) return "Enter a valid email address.";
        if (!PhoneValidationUtil.isSupportedCountryCode(code)) return "Country Code is not supported (e.g. +91).";
        if (!phone.matches("\\d+") || !PhoneValidationUtil.isValid(code, phone)) {
            return "Phone number length is not valid for " + code + ".";
        }
        if (!ALLOWED_ROLES.contains(roleName.toLowerCase())) return "Role must be Learner or Instructor.";
        if (!roles.containsKey(roleName.toLowerCase())) return "Role '" + roleName + "' is not available (create/activate it in Role Management).";
        if (dept.length() > 100) return "Department can have at most 100 characters.";
        if (!seen.add(email)) return "Duplicate email inside the file.";
        if (userRepository.existsByEmailIgnoreCase(email)) return "This email is already registered.";
        return null;
    }

    /** Returns false when the mail could not be sent - the user still exists and can use Forgot password. */
    boolean sendSetPasswordMail(User user) {
        try {
            String raw = TokenGenerator.generateRawToken();
            tokenRepository.save(PasswordResetToken.builder()
                    .email(user.getEmail())
                    .tokenHash(TokenGenerator.hash(raw))
                    .expiryTime(LocalDateTime.now().plusMinutes(setPasswordExpiryMinutes))
                    .used(false).build());
            emailService.sendWelcomeSetPasswordEmail(
                    user.getEmail(), resetLinkBaseUrl + "?token=" + raw, user.getRole(), setPasswordExpiryMinutes);
            return true;
        } catch (RuntimeException e) {
            log.warn("User import: welcome mail failed for user id {}: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Sample template                                                   */
    /* ------------------------------------------------------------------ */

    public byte[] buildTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Users");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@")); // keep +91 / leading zeros

            Row h = sheet.createRow(0);
            for (int c = 0; c < HEADERS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(HEADERS.get(c) + (c < 6 ? "*" : ""));
                cell.setCellStyle(head);
                sheet.setDefaultColumnStyle(c, text);
                sheet.setColumnWidth(c, 5500);
            }
            String[][] sample = {
                    {"Asha", "Kumar", "asha.kumar@example.com", "+91", "9876543210", "Learner", "Cyber security"},
                    {"Ravi", "Shankar", "ravi.shankar@example.com", "+91", "9123456780", "Instructor", "Data science"}};
            for (int r = 0; r < sample.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sample[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(text);
                    cell.setCellValue(sample[r][c]);
                }
            }
            DataValidationHelper dv = sheet.getDataValidationHelper();
            DataValidation roleList = dv.createValidation(
                    dv.createExplicitListConstraint(new String[]{"Learner", "Instructor"}),
                    new CellRangeAddressList(1, MAX_ROWS, 5, 5));
            roleList.setShowErrorBox(true);
            sheet.addValidationData(roleList);

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Fill the 'Users' sheet. Columns marked * are required. Delete the two sample rows first.",
                    "Role: Learner or Instructor (must exist and be Active in Role Management).",
                    "Country Code: with +, e.g. +91. Phone Number: digits only, without the country code.",
                    "Email must be new - already registered emails are skipped and reported.",
                    "Maximum " + MAX_ROWS + " users per file. Do not rename or reorder the header row.",
                    "Imported users receive an email to set their own password."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 14000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Unable to create the template.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private void validateHeader(Row header, DataFormatter fmt) {
        if (header == null) throw new ApiException("The file is empty.", HttpStatus.BAD_REQUEST);
        for (int c = 0; c < HEADERS.size(); c++) {
            String got = cell(header, c, fmt).replace("*", "").trim();
            if (!HEADERS.get(c).equalsIgnoreCase(got)) {
                throw new ApiException("Header row does not match the sample template (expected '"
                        + HEADERS.get(c) + "' in column " + (c + 1) + ").", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static boolean isBlank(Row row, DataFormatter fmt) {
        for (int c = 0; c < HEADERS.size(); c++) if (!cell(row, c, fmt).isEmpty()) return false;
        return true;
    }

    private static String cell(Row row, int col, DataFormatter fmt) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }
}