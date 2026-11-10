package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.EnrollmentDto;
import com.example.lms.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Access is permission based (Role Management / Package): Enrolled Course List (read) and
 * Enroll/Unenroll in Course (create / delete). A role without them has no access to these endpoints.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
@RequiredArgsConstructor
public class EnrollmentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final EnrollmentService enrollmentService;

    /** Enrolled Course List for the selected course. */
    @GetMapping("/enrollments")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLLED_LIST', 'read')")
    public ResponseEntity<ApiResponse<Page<EnrollmentDto.EnrolledLearnerResponse>>> getEnrolledLearners(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(ApiResponse.success(
                "Enrolled course list fetched successfully.",
                enrollmentService.getEnrolledLearners(courseId, pageable)));
    }

    /** Options for the Enroll User Form's multi-select (active learners not yet enrolled). */
    @GetMapping("/eligible-users")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'create')")
    public ResponseEntity<ApiResponse<List<EnrollmentDto.EligibleUserResponse>>> getEligibleUsers(
            @PathVariable Long courseId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                "Eligible users fetched successfully.",
                enrollmentService.getEligibleUsers(courseId, search, limit)));
    }

    /** "Enroll Selected User(s)" button. */
    @PostMapping("/enrollments")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'create')")
    public ResponseEntity<ApiResponse<EnrollmentDto.EnrollmentActionResponse>> enrollUsers(
            @PathVariable Long courseId,
            @Valid @RequestBody EnrollmentDto.UserIdsRequest request) {
        EnrollmentDto.EnrollmentActionResponse result = enrollmentService.enrollUsers(courseId, request.getUserIds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Selected user(s) enrolled successfully.", result));
    }

    /** More Actions (⋮) -> Unenroll. POST (not DELETE) because it carries a body of ids. */
    @PostMapping("/enrollments/unenroll")
    @PreAuthorize("@perm.can(authentication, 'ENROLLMENT_ENROLL_UNENROLL', 'delete')")
    public ResponseEntity<ApiResponse<EnrollmentDto.EnrollmentActionResponse>> unenrollUsers(
            @PathVariable Long courseId,
            @Valid @RequestBody EnrollmentDto.UserIdsRequest request) {
        EnrollmentDto.EnrollmentActionResponse result = enrollmentService.unenrollUsers(courseId, request.getUserIds());
        return ResponseEntity.ok(ApiResponse.success("Selected user(s) unenrolled successfully.", result));
    }
}