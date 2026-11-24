package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.BulkEnrollmentDto;
import com.example.lms.service.BulkEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/enroll")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'create')")
    public ResponseEntity<ApiResponse<BulkEnrollmentDto.Result>> enroll(
            @Valid @RequestBody BulkEnrollmentDto.Request request) {
        BulkEnrollmentDto.Result r = service.enroll(request.getCourseId(), request.getUserIds());
        return ResponseEntity.ok(ApiResponse.success(r.getAffected() + " user(s) enrolled.", r));
    }

    @PostMapping("/unenroll")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'delete')")
    public ResponseEntity<ApiResponse<BulkEnrollmentDto.Result>> unenroll(
            @Valid @RequestBody BulkEnrollmentDto.Request request) {
        BulkEnrollmentDto.Result r = service.unenroll(request.getCourseId(), request.getUserIds());
        return ResponseEntity.ok(ApiResponse.success(r.getAffected() + " user(s) unenrolled.", r));
    }
}