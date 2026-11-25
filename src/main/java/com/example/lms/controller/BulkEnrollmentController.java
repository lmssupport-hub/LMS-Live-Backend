package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.BulkEnrollmentDto;
import com.example.lms.service.BulkEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * All Users > Bulk Users > Enroll in course / Unenroll from course.
 * Same permission feature as the course page: ENROLLMENT_ENROLL_UNENROLL (create = enroll, delete = unenroll).
 */
@RestController
@RequestMapping("/api/users/bulk-enrollment")
@RequiredArgsConstructor
public class BulkEnrollmentController {

    private final BulkEnrollmentService service;

    /** Course dropdown of the modal. */
    @GetMapping("/courses")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'create') "
            + "or @perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'delete')")
    public ResponseEntity<ApiResponse<List<BulkEnrollmentDto.CourseOption>>> courses() {
        return ResponseEntity.ok(ApiResponse.success("Courses fetched successfully.", service.listCourses()));
    }

    @PostMapping("/unenroll")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'delete')")
    public ResponseEntity<ApiResponse<BulkEnrollmentDto.Result>> unenroll(
            @Valid @RequestBody BulkEnrollmentDto.Request request) {
        BulkEnrollmentDto.Result r = service.unenroll(request.getCourseId(), request.getUserIds());
        return ResponseEntity.ok(ApiResponse.success(r.getAffected() + " user(s) unenrolled.", r));
    }

    /* ---- Enroll in course via Excel (creates missing users, then enrolls) ---- */

    /** Creating users needs the All Users 'update' permission too, same as Import users. */
    private static final String IMPORT_ACCESS =
            "@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'create') "
                    + "and @perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')";

    @GetMapping("/import/template")
    @PreAuthorize(IMPORT_ACCESS)
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enroll-users-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.buildTemplate());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(IMPORT_ACCESS)
    public ResponseEntity<ApiResponse<BulkEnrollmentDto.ImportResult>> importAndEnroll(
            @RequestPart("file") MultipartFile file) {
        BulkEnrollmentDto.ImportResult r = service.importAndEnroll(file);
        return ResponseEntity.ok(ApiResponse.success(
                r.getEnrolled() + " enrolled, " + r.getUsersCreated() + " new user(s) created.", r));
    }

    /* ---- Unenroll via Excel (email + course per row) ---- */

    private static final String UNENROLL_IMPORT_ACCESS =
            "@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'delete')";

    @GetMapping("/unenroll-import/template")
    @PreAuthorize(UNENROLL_IMPORT_ACCESS)
    public ResponseEntity<byte[]> unenrollTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"unenroll-users-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.buildUnenrollTemplate());
    }

    @PostMapping(value = "/unenroll-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(UNENROLL_IMPORT_ACCESS)
    public ResponseEntity<ApiResponse<BulkEnrollmentDto.UnenrollImportResult>> unenrollImport(
            @RequestPart("file") MultipartFile file) {
        BulkEnrollmentDto.UnenrollImportResult r = service.importAndUnenroll(file);
        return ResponseEntity.ok(ApiResponse.success(r.getUnenrolled() + " user(s) unenrolled.", r));
    }
}