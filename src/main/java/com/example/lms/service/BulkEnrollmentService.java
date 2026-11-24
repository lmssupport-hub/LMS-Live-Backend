package com.example.lms.service;

import com.example.lms.dto.BulkEnrollmentDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bulk enroll / unenroll from the All Users page.
 *
 * Unlike EnrollmentService (course page: all-or-nothing, learners only picked from an eligible list),
 * the ticked users here can be anyone in the table, so every user is judged on his own:
 * the valid ones are enrolled / unenrolled, the rest come back in {@code skipped} with a reason.
 * Everything is scoped to the caller's company (course AND users).
 */
@Service
@RequiredArgsConstructor
public class BulkEnrollmentService {

    static final int MAX_USERS = 200;

    private final CourseAccessService courseAccess;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

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
    public BulkEnrollmentDto.Result enroll(Long courseId, List<Long> rawIds) {
        CourseEntity course = courseAccess.requireCourseInOrganization(courseId);
        Set<Long> ids = normalize(rawIds);
        Map<Long, User> users = companyUsers(ids);
        Set<Long> alreadyEnrolled = new HashSet<>(enrollmentRepository.findEnrolledUserIds(courseId, ids));

        List<BulkEnrollmentDto.Skipped> skipped = new ArrayList<>();
        List<EnrollmentEntity> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Long id : ids) {
            User u = users.get(id);
            String reason = null;
            if (u == null) reason = "User not found.";
            else if (!"LEARNER".equalsIgnoreCase(u.getRole())) reason = "Only learners can be enrolled (role: " + u.getRole() + ").";
            else if (!Boolean.TRUE.equals(u.getActive())) reason = "User is inactive.";
            else if (alreadyEnrolled.contains(id)) reason = "Already enrolled in this course.";

            if (reason != null) {
                skipped.add(skip(id, u, reason));
            } else {
                toSave.add(EnrollmentEntity.builder()
                        .courseId(courseId).userId(id).enrolledAt(now)
                        .progressPercent(0).status(EnrollmentStatus.NOT_STARTED).build());
            }
        }

        try {
            enrollmentRepository.saveAllAndFlush(toSave);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "Some users were enrolled by someone else at the same time. Please try again.", HttpStatus.CONFLICT);
        }
        return result(course, ids.size(), toSave.size(), skipped);
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
}