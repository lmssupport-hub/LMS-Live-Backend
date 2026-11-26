package com.example.lms.service;

import com.example.lms.dto.SuperAdminAdminDto;
import com.example.lms.dto.UserImportDto;
import com.example.lms.entity.OrganizationDetails;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.OrganizationDetailsRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.CsvUtil;
import com.example.lms.util.PhoneValidationUtil;
import com.example.lms.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Super Admin > Admins > Bulk Admin's: Import (.xlsx) and Export as CSV.
 *
 * Import follows All Users > Import users ({@link UserImportService}), except there is no Role
 * column: every imported row is a company ADMIN. As on sign-up, the auto-assign Free package is
 * applied (an admin without a package cannot log in).
 */
@Service
@RequiredArgsConstructor
public class SuperAdminAdminBulkService {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminAdminBulkService.class);

    private static final String ADMIN_ROLE = "ADMIN";
    private static final List<String> HEADERS = List.of(
            "First Name", "Last Name", "Email", "Country Code", "Phone Number", "Department");
    /** Every column before Department is required. */
    private static final int REQUIRED_COLUMNS = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DIAL_CODE = Pattern.compile("^\\+\\d+$");

    private final UserRepository userRepository;
    private final OrganizationDetailsRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final PackageService packageService;
    private final UserImportService userImportService;
    private final TransactionTemplate transactionTemplate;

    /* ------------------------------------------------------------------ */
    /*  Import                                                            */
    /* ------------------------------------------------------------------ */

    public SuperAdminAdminDto.ImportResult importAdmins(Long actorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Please choose an Excel file.", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw new ApiException("Only .xlsx files are supported. Download the sample template.", HttpStatus.BAD_REQUEST);
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
                if (++total > UserImportService.MAX_ROWS) {
                    throw new ApiException("A file can contain at most " + UserImportService.MAX_ROWS + " admins.",
                            HttpStatus.BAD_REQUEST);
                }
                String first = cell(row, 0, fmt), last = cell(row, 1, fmt);
                String email = cell(row, 2, fmt).toLowerCase();
                String code = cell(row, 3, fmt), phone = cell(row, 4, fmt).replaceAll("[\\s-]", "");
                String dept = cell(row, 5, fmt);
                if (!code.isEmpty() && !code.startsWith("+")) code = "+" + code;

                String problem = validate(first, last, email, code, phone, dept, seenInFile);
                if (problem != null) {
                    errors.add(new UserImportDto.RowError(i + 1, email, problem));
                    continue;
                }
                toSave.add(User.builder()
                        .firstName(first).lastName(last).email(email)
                        .countryCode(code).phoneNumber(phone)
                        .password(passwordEncoder.encode(TokenGenerator.generateRawToken())) // unusable until reset
                        .role(ADMIN_ROLE) // always, whatever the file says
                        .department(dept.isEmpty() ? null : dept)
                        .acceptedTerms(true).active(true)
                        .build());
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Admin import: unreadable workbook: {}", e.getMessage());
            throw new ApiException("The file could not be read. Please use the sample template (.xlsx).",
                    HttpStatus.BAD_REQUEST);
        }

        if (total == 0) {
            throw new ApiException("No admins found in the file.", HttpStatus.BAD_REQUEST);
        }

        // One transaction per row (account + auto package), so one conflict cannot sink the rest.
        int imported = 0, emailsFailed = 0, withoutPackage = 0;
        for (User u : toSave) {
            try {
                User saved = transactionTemplate.execute(tx -> {
                    User created = userRepository.save(u);
                    packageService.assignAutoPackageOnSignup(created);
                    return created;
                });
                imported++;
                if (saved.getPackageId() == null) withoutPackage++;
                if (!userImportService.sendSetPasswordMail(saved)) emailsFailed++;
            } catch (DataIntegrityViolationException e) {
                errors.add(new UserImportDto.RowError(0, u.getEmail(), "This email is already registered."));
            }
        }
        errors.sort(Comparator.comparingInt(UserImportDto.RowError::getRow));
        log.info("Super admin {} imported {} of {} admins", actorId, imported, total);

        return SuperAdminAdminDto.ImportResult.builder()
                .total(total).imported(imported).failed(errors.size())
                .emailsFailed(emailsFailed).withoutPackage(withoutPackage).errors(errors).build();
    }

    private String validate(String first, String last, String email, String code, String phone,
                            String dept, Set<String> seen) {
        if (first.isEmpty() || last.isEmpty()) return "First Name and Last Name are required.";
        if (first.length() > 50 || last.length() > 50) return "Names can have at most 50 characters.";
        if (email.isEmpty() || !EMAIL.matcher(email).matches() || email.length() > 100) return "Enter a valid email address.";
        if (!PhoneValidationUtil.isSupportedCountryCode(code)) return "Country Code is not supported (e.g. +91).";
        if (!phone.matches("\\d+") || !PhoneValidationUtil.isValid(code, phone)) {
            return "Phone number length is not valid for " + code + ".";
        }
        if (dept.length() > 100) return "Department can have at most 100 characters.";
        if (!seen.add(email)) return "Duplicate email inside the file.";
        if (userRepository.existsByEmailIgnoreCase(email)) return "This email is already registered.";
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*  Sample template                                                   */
    /* ------------------------------------------------------------------ */

    public byte[] buildTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Admins");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@")); // keep +91 / leading zeros

            Row h = sheet.createRow(0);
            for (int c = 0; c < HEADERS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(HEADERS.get(c) + (c < REQUIRED_COLUMNS ? "*" : ""));
                cell.setCellStyle(head);
                sheet.setDefaultColumnStyle(c, text);
                sheet.setColumnWidth(c, 5500);
            }
            String[] sample = {"Asha", "Kumar", "asha.kumar@example.com", "+91", "9876543210", "Operations"};
            Row row = sheet.createRow(1);
            for (int c = 0; c < sample.length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellStyle(text);
                cell.setCellValue(sample[c]);
            }

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "Fill the 'Admins' sheet. Columns marked * are required. Delete the sample row first.",
                    "There is no Role column: every imported account is an Admin.",
                    "Country Code: with +, e.g. +91. Phone Number: digits only, without the country code.",
                    "Email must be new - already registered emails are skipped and reported.",
                    "Maximum " + UserImportService.MAX_ROWS + " admins per file. Do not rename or reorder the header row.",
                    "Imported admins receive an email to set their own password."};
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 14000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Unable to create the template.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Export as CSV                                                     */
    /* ------------------------------------------------------------------ */

    /** Company admins only; ids that are not one are ignored. Null / empty ids = all of them. */
    public byte[] exportCsv(List<Long> ids) {
        List<User> admins = userRepository
                .findByRoleAndCompanyAdminIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdAsc(ADMIN_ROLE);
        if (ids != null && !ids.isEmpty()) {
            Set<Long> wanted = new HashSet<>(ids);
            admins = admins.stream().filter(u -> wanted.contains(u.getId())).toList();
        }

        Map<Long, String> organizations = new HashMap<>();
        for (OrganizationDetails o : organizationRepository.findByUserIdIn(admins.stream().map(User::getId).toList())) {
            organizations.put(o.getUser().getId(), o.getOrganizationName());
        }

        LocalDate today = LocalDate.now();
        StringBuilder csv = new StringBuilder("﻿"); // BOM: Excel then reads UTF-8 names correctly
        csv.append(CsvUtil.toLine(List.of("First Name", "Last Name", "Email", "Country Code", "Phone Number",
                "Department", "Organization", "Role", "Status")));
        for (User u : admins) {
            // A plain "+91" is not a formula, so keep it out of the injection guard that would add a quote.
            String code = text(u.getCountryCode());
            csv.append(CsvUtil.escape(u.getFirstName())).append(',')
                    .append(CsvUtil.escape(u.getLastName())).append(',')
                    .append(CsvUtil.escape(u.getEmail())).append(',')
                    .append(DIAL_CODE.matcher(code).matches() ? code : CsvUtil.escape(code)).append(',')
                    .append(CsvUtil.toLine(List.of(
                            text(u.getPhoneNumber()),
                            text(u.getDepartment()),
                            text(organizations.get(u.getId())),
                            "Admin",
                            UserManagementService.statusOf(u, today))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private static String text(String value) {
        return value == null ? "" : value;
    }

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