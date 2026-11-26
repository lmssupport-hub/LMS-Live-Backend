package com.example.lms.service;

import com.example.lms.dto.ProfileDto;
import com.example.lms.dto.UserImportDto;
import com.example.lms.dto.UserManagementDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * "All Users" page: list / status / delete / edit-profile of the team members of ONE company.
 *
 * Rules enforced here:
 *  - WHO may use which action is decided by the package / role permission on the controller
 *    (USER_MGMT_ALL_USERS read / update / delete);
 *  - every target user must belong to the caller's company (404 otherwise - same answer for
 *    "does not exist" and "belongs to someone else", so ids cannot be probed). The company ADMIN
 *    is never a target (it has no companyAdminId);
 *  - the caller can never change their own status or delete themselves.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTIVE = "Active";
    private static final String INACTIVE = "Inactive";
    private static final String SUSPENDED = "Suspended";
    private static final int DELETE_IMPORT_MAX_ROWS = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final ProfileService profileService;

    /* ------------------------------------------------------------------ */
    /*  List                                                              */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public UserManagementDto.PageResponse list(Long actorId, String search, String status, int page, int size) {
        User actor = requireManager(actorId);

        String statusFilter = normaliseStatusFilter(status);
        String term = search == null ? null : search.trim();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Page<User> result = userRepository.searchManagedUsers(
                PackageService.resolveOwnerId(actor),
                term == null || term.isEmpty() ? null : term,
                statusFilter,
                LocalDate.now(),
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));

        LocalDate today = LocalDate.now();
        return UserManagementDto.PageResponse.builder()
                .content(result.getContent().stream().map(u -> toRow(u, today)).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /* ------------------------------------------------------------------ */
    /*  Status (Active / Inactive / Suspended)                            */
    /* ------------------------------------------------------------------ */

    @Transactional
    public UserManagementDto.Row updateStatus(Long actorId, Long targetId, UserManagementDto.StatusRequest req) {
        User actor = requireManager(actorId);
        User target = findManaged(actor, targetId);
        rejectSelf(actor, target, "You cannot change your own status.");

        LocalDate today = LocalDate.now();
        applyStatus(target, req, today);

        userRepository.save(target);
        log.info("User {} set to {} by {}", target.getId(), req.getStatus(), actor.getId());
        return toRow(target, today);
    }

    /**
     * Active / Inactive / Suspended rules. Shared with Super Admin > Admins
     * ({@link SuperAdminAdminService}) so both screens behave the same way.
     */
    static void applyStatus(User target, UserManagementDto.StatusRequest req, LocalDate today) {
        switch (req.getStatus()) {
            case ACTIVE -> {
                target.setActive(true);
                clearSuspension(target);
            }
            case INACTIVE -> {
                target.setActive(false);
                clearSuspension(target);
                forceOffline(target);
            }
            case SUSPENDED -> {
                LocalDate from = req.getSuspendFrom();
                LocalDate to = req.getSuspendTo();
                if (from == null || to == null) {
                    throw new ApiException("Suspension start and end dates are required.", HttpStatus.BAD_REQUEST);
                }
                if (to.isBefore(from)) {
                    throw new ApiException("Suspension end date cannot be before the start date.", HttpStatus.BAD_REQUEST);
                }
                if (to.isBefore(today)) {
                    throw new ApiException("Suspension end date cannot be in the past.", HttpStatus.BAD_REQUEST);
                }
                target.setActive(true);
                target.setSuspendedFrom(from);
                target.setSuspendedTo(to);
                if (target.isSuspendedOn(today)) {
                    forceOffline(target);
                }
            }
            default -> throw new ApiException("Status must be Active, Inactive or Suspended.", HttpStatus.BAD_REQUEST);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Delete (soft)                                                     */
    /* ------------------------------------------------------------------ */

    @Transactional
    public void delete(Long actorId, Long targetId) {
        User actor = requireManager(actorId);
        User target = findManaged(actor, targetId);
        rejectSelf(actor, target, "You cannot delete your own account.");
        softDelete(target);
        log.info("User {} deleted by {}", target.getId(), actor.getId());
    }

    @Transactional
    public UserManagementDto.BulkDeleteResponse bulkDelete(Long actorId, List<Long> ids) {
        User actor = requireManager(actorId);
        Set<Long> requested = new HashSet<>(ids);
        if (requested.contains(actor.getId())) {
            throw new ApiException("You cannot delete your own account.", HttpStatus.BAD_REQUEST);
        }

        List<User> owned = userRepository.findByIdInAndCompanyAdminIdAndDeletedAtIsNull(
                requested, PackageService.resolveOwnerId(actor));
        Set<Long> deletedIds = new HashSet<>();
        for (User u : owned) {
            softDelete(u);
            deletedIds.add(u.getId());
        }

        List<Long> skipped = new ArrayList<>(requested);
        skipped.removeAll(deletedIds);
        log.info("{} user(s) bulk-deleted by {}", deletedIds.size(), actor.getId());
        return UserManagementDto.BulkDeleteResponse.builder()
                .deleted(deletedIds.size())
                .skipped(skipped)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /*  Bulk delete via Excel (one email per row)                         */
    /* ------------------------------------------------------------------ */

    /**
     * Deletes (soft delete, same as the normal delete) every user whose email is listed in the sheet.
     * A row is rejected - and reported with its reason - when the email is invalid, appears twice,
     * is the caller's own account, or does not belong to a (non-deleted) user of the caller's company.
     * Valid rows are deleted even when other rows fail.
     */
    @Transactional
    public UserManagementDto.BulkDeleteImportResult bulkDeleteImport(Long actorId, MultipartFile file) {
        User actor = requireManager(actorId);
        Long ownerId = PackageService.resolveOwnerId(actor);

        if (file == null || file.isEmpty()) {
            throw new ApiException("Please choose an Excel file.", HttpStatus.BAD_REQUEST);
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            throw new ApiException("Only .xlsx files are supported. Download the sample template.", HttpStatus.BAD_REQUEST);
        }

        // (row number, email) pairs, blank rows skipped.
        List<Object[]> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.Row header = sheet.getRow(0);
            String head = header == null ? "" : fmt.formatCellValue(header.getCell(0)).replace("*", "").trim();
            if (!"Email".equalsIgnoreCase(head)) {
                throw new ApiException("Header row does not match the sample template (expected 'Email' in column 1).",
                        HttpStatus.BAD_REQUEST);
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                String email = row == null ? "" : fmt.formatCellValue(row.getCell(0)).trim().toLowerCase();
                if (email.isEmpty()) continue;
                if (rows.size() >= DELETE_IMPORT_MAX_ROWS) {
                    throw new ApiException("A file can contain at most " + DELETE_IMPORT_MAX_ROWS + " rows.",
                            HttpStatus.BAD_REQUEST);
                }
                rows.add(new Object[]{i + 1, email});
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Delete import: unreadable workbook: {}", e.getMessage());
            throw new ApiException("The file could not be read. Please use the sample template (.xlsx).", HttpStatus.BAD_REQUEST);
        }
        if (rows.isEmpty()) {
            throw new ApiException("No rows found in the file.", HttpStatus.BAD_REQUEST);
        }

        List<UserImportDto.RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int deleted = 0;
        for (Object[] r : rows) {
            int rowNo = (Integer) r[0];
            String email = (String) r[1];
            if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 100) {
                errors.add(new UserImportDto.RowError(rowNo, email, "Enter a valid email address."));
                continue;
            }
            if (!seen.add(email)) {
                errors.add(new UserImportDto.RowError(rowNo, email, "Duplicate row inside the file."));
                continue;
            }
            User target = userRepository.findByEmailIgnoreCase(email)
                    .filter(u -> u.getDeletedAt() == null && java.util.Objects.equals(u.getCompanyAdminId(), ownerId))
                    .orElse(null);
            if (target == null) {
                errors.add(new UserImportDto.RowError(rowNo, email, "No user with this email in your organization."));
                continue;
            }
            if (target.getId().equals(actor.getId())) {
                errors.add(new UserImportDto.RowError(rowNo, email, "You cannot delete your own account."));
                continue;
            }
            softDelete(target);
            deleted++;
        }
        errors.sort(Comparator.comparingInt(UserImportDto.RowError::getRow));
        log.info("{} user(s) deleted via Excel by {}", deleted, actor.getId());
        return UserManagementDto.BulkDeleteImportResult.builder()
                .total(rows.size()).deleted(deleted).failed(errors.size()).errors(errors).build();
    }

    public byte[] bulkDeleteTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Delete users");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            Cell h = sheet.createRow(0).createCell(0);
            h.setCellValue("Email*");
            h.setCellStyle(head);
            sheet.setDefaultColumnStyle(0, text);
            sheet.setColumnWidth(0, 9000);
            Cell sample = sheet.createRow(1).createCell(0);
            sample.setCellStyle(text);
            sample.setCellValue("asha.kumar@example.com");

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "List one email per row in the 'Delete users' sheet. Delete the sample row first.",
                    "Every listed user of your organization is deleted. Deleted users cannot log in any more.",
                    "Rows with an invalid email, a duplicate, your own email, or an email that is not a user of your organization are skipped and listed with the reason.",
                    "Maximum " + DELETE_IMPORT_MAX_ROWS + " rows per file. Do not rename the header."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 16000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Unable to create the template.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Edit Profile of ANOTHER user (reuses ProfileService)              */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ProfileDto.Response getProfile(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.getMyProfile(target.getId());
    }

    @Transactional
    public ProfileDto.Response updateProfile(Long actorId, Long targetId, ProfileDto.UpdateRequest req) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateProfile(target.getId(), req, true);
    }

    @Transactional
    public ProfileDto.Response uploadProfilePicture(Long actorId, Long targetId, MultipartFile file) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateProfilePicture(target.getId(), file);
    }

    @Transactional
    public ProfileDto.Response uploadBackgroundPicture(Long actorId, Long targetId, MultipartFile file) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateBackgroundPicture(target.getId(), file);
    }

    @Transactional
    public ProfileDto.Response removeProfilePicture(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.removeProfilePicture(target.getId());
    }

    @Transactional
    public ProfileDto.Response removeBackgroundPicture(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.removeBackgroundPicture(target.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** The logged-in user (their company is derived from it). */
    private User requireManager(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
    }

    /** Same 404 for "does not exist", "already deleted" and "another company's user". */
    private User findManaged(User actor, Long targetId) {
        return userRepository
                .findByIdAndCompanyAdminIdAndDeletedAtIsNull(targetId, PackageService.resolveOwnerId(actor))
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    private static void rejectSelf(User actor, User target, String message) {
        if (actor.getId().equals(target.getId())) {
            throw new ApiException(message, HttpStatus.BAD_REQUEST);
        }
    }

    private static void clearSuspension(User user) {
        user.setSuspendedFrom(null);
        user.setSuspendedTo(null);
    }

    private static void forceOffline(User user) {
        user.setOnline(false);
    }

    /**
     * Keeps the row (payments, certificates, progress still point at it) but disables it and frees
     * the email so the same address can be invited again. Name/phone stay for those records.
     */
    private void softDelete(User user) {
        markDeleted(user);
        userRepository.save(user);
    }

    /** The soft-delete field changes (the caller saves). Shared with Super Admin > Admins. */
    static void markDeleted(User user) {
        user.setActive(false);
        user.setOnline(false);
        clearSuspension(user);
        user.setDeletedAt(LocalDateTime.now());
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setTwoFaEnabled(false);
        user.setTwoFaMethod(null);
        user.setTwoFaSecret(null);
    }

    private static String normaliseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "inactive" -> INACTIVE;
            case "suspended" -> SUSPENDED;
            default -> throw new ApiException("Status must be Active, Inactive or Suspended.", HttpStatus.BAD_REQUEST);
        };
    }

    static String statusOf(User user, LocalDate today) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            return INACTIVE;
        }
        return user.isSuspendedOn(today) ? SUSPENDED : ACTIVE;
    }

    private static UserManagementDto.Row toRow(User u, LocalDate today) {
        return UserManagementDto.Row.builder()
                .id(u.getId())
                .username((u.getFirstName() + " " + u.getLastName()).trim())
                .role(u.getRole())
                .email(u.getEmail())
                .status(statusOf(u, today))
                .department(u.getDepartment())
                .suspendFrom(u.getSuspendedFrom())
                .suspendTo(u.getSuspendedTo())
                .build();
    }
}