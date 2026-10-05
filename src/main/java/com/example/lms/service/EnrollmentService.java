package com.example.lms.service;

import com.example.lms.dto.EnrollmentDto;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    /** Upper bound per request: keeps the IN (...) lists and the transaction small. */
    static final int MAX_USERS_PER_REQUEST = 200;
    static final int MAX_ELIGIBLE_RESULTS = 100;

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    // ------------------------------------------------------------------
    // Enrolled Course List (M4F2)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<EnrollmentDto.EnrolledLearnerResponse> getEnrolledLearners(Long courseId, Pageable pageable) {
        requireCourse(courseId);
        // Sorting is fixed inside the query; drop any client-supplied sort so it cannot break the join.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return enrollmentRepository.findEnrolledLearners(courseId, unsorted).map(this::toResponse);
    }

    /** Options for the Enroll User Form's User multi-select: active learners not yet enrolled. */
    @Transactional(readOnly = true)
    public List<EnrollmentDto.EligibleUserResponse> getEligibleUsers(Long courseId, String search, int limit) {
        requireCourse(courseId);
        int size = Math.min(Math.max(limit, 1), MAX_ELIGIBLE_RESULTS);
        return enrollmentRepository
                .findEligibleLearners(courseId, escapeLike(search), PageRequest.of(0, size))
                .stream()
                .map(r -> EnrollmentDto.EligibleUserResponse.builder()
                        .id(r.getId())
                        .name(fullName(r.getFirstName(), r.getLastName()))
                        .email(r.getEmail())
                        .build())
                .toList();
    }

    // ------------------------------------------------------------------
    // Enroll (M4F1)
    // ------------------------------------------------------------------

    /**
     * All-or-nothing: either every selected user is enrolled or none is
     * (SRS Edge Cases #4 / #9 - no partial enrollment, data stays consistent).
     */
    @Transactional
    public EnrollmentDto.EnrollmentActionResponse enrollUsers(Long courseId, List<Long> rawUserIds) {
        requireCourse(courseId);
        Set<Long> userIds = normalize(rawUserIds);

        // Server-side eligibility: ids must be real, active learners (never trust the client's list).
        if (enrollmentRepository.countEligibleLearners(userIds) != userIds.size()) {
            throw new ApiException("One or more selected users are not eligible for enrollment.", HttpStatus.BAD_REQUEST);
        }

        // Already enrolled => the form was stale (another admin enrolled them first).
        if (!enrollmentRepository.findEnrolledUserIds(courseId, userIds).isEmpty()) {
            throw new ApiException(
                    "One or more selected users are already enrolled in this course. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        List<EnrollmentEntity> enrollments = userIds.stream()
                .map(userId -> EnrollmentEntity.builder()
                        .courseId(courseId)
                        .userId(userId)
                        .enrolledAt(now)
                        .progressPercent(0)
                        .status(EnrollmentStatus.NOT_STARTED)
                        .build())
                .toList();

        try {
            // Flush inside the try so a unique-constraint race surfaces HERE, not at commit time.
            enrollmentRepository.saveAllAndFlush(enrollments);
        } catch (DataIntegrityViolationException e) {
            log.warn("Enrollment race detected. courseId={}, userCount={}", courseId, userIds.size());
            throw new ApiException(
                    "One or more selected users were enrolled by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        return EnrollmentDto.EnrollmentActionResponse.builder()
                .courseId(courseId)
                .affectedCount(enrollments.size())
                .build();
    }

    // ------------------------------------------------------------------
    // Unenroll (M4F1)
    // ------------------------------------------------------------------

    @Transactional
    public EnrollmentDto.EnrollmentActionResponse unenrollUsers(Long courseId, List<Long> rawUserIds) {
        requireCourse(courseId);
        Set<Long> userIds = normalize(rawUserIds);

        int deleted = enrollmentRepository.deleteByCourseIdAndUserIdIn(courseId, userIds);
        if (deleted != userIds.size()) {
            // Someone else already unenrolled one of them. Throwing rolls the whole delete back,
            // so the existing enrollment state is retained (SRS Edge Cases #5 / #7 / #10).
            throw new ApiException(
                    "One or more selected users are no longer enrolled in this course. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        return EnrollmentDto.EnrollmentActionResponse.builder()
                .courseId(courseId)
                .affectedCount(deleted)
                .build();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void requireCourse(Long courseId) {
        if (courseId == null || !courseRepository.existsById(courseId)) {
            throw new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND);
        }
    }

    /** De-duplicates, rejects empty / null / oversized selections. Order is preserved. */
    private Set<Long> normalize(List<Long> rawUserIds) {
        if (rawUserIds == null || rawUserIds.isEmpty()) {
            throw new ApiException("Please select at least one user", HttpStatus.BAD_REQUEST);
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : rawUserIds) {
            if (id == null || id <= 0) {
                throw new ApiException("User is required.", HttpStatus.BAD_REQUEST);
            }
            ids.add(id);
        }
        if (ids.size() > MAX_USERS_PER_REQUEST) {
            throw new ApiException(
                    "You can select at most " + MAX_USERS_PER_REQUEST + " users at a time.", HttpStatus.BAD_REQUEST);
        }
        return ids;
    }

    /** Escapes LIKE wildcards using '!' (matches the "escape '!'" clause in the repository query). */
    private String escapeLike(String search) {
        if (search == null) return "";
        String s = search.trim();
        if (s.length() > 100) s = s.substring(0, 100);
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String fullName(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    private EnrollmentDto.EnrolledLearnerResponse toResponse(EnrollmentRepository.EnrolledLearnerRow r) {
        LocalDate completionDate = r.getCompletedAt() == null ? null : r.getCompletedAt().toLocalDate();
        return EnrollmentDto.EnrolledLearnerResponse.builder()
                .enrollmentId(r.getEnrollmentId())
                .userId(r.getUserId())
                .userName(fullName(r.getFirstName(), r.getLastName()))
                .enrollmentDate(r.getEnrolledAt() == null ? null : r.getEnrolledAt().toLocalDate())
                .completionDate(completionDate)
                .expirationDate(r.getExpiresAt() == null ? null : r.getExpiresAt().toLocalDate())
                .progressPercent(r.getProgressPercent())
                .status(r.getStatus() == null ? EnrollmentStatus.NOT_STARTED.getLabel() : r.getStatus().getLabel())
                .scorePercent(r.getScorePercent())
                .completion(completionDate == null ? "Not completed" : "Completed on " + completionDate)
                .build();
    }
}