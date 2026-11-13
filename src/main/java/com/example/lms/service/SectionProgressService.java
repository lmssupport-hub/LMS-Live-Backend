package com.example.lms.service;

import com.example.lms.dto.SectionProgressDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.SectionEntity;
import com.example.lms.entity.SectionProgressEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.SectionProgressRepository;
import com.example.lms.repository.SectionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-module progress of a learner (shown as the rings on the Course Preview page) and the
 * roll-up into course_enrollments.progress_percent / status.
 */
@Service
public class SectionProgressService {

    private final CourseAccessService courseAccess;
    private final SectionRepository sectionRepository;
    private final SectionProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;

    public SectionProgressService(CourseAccessService courseAccess,
                                  SectionRepository sectionRepository,
                                  SectionProgressRepository progressRepository,
                                  EnrollmentRepository enrollmentRepository) {
        this.courseAccess = courseAccess;
        this.sectionRepository = sectionRepository;
        this.progressRepository = progressRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public SectionProgressDto.Response getProgress(Long courseId) {
        User user = courseAccess.currentUser();
        courseAccess.requireReadableCourse(courseId);
        if (!isTrackedLearner(user, courseId)) {
            return untracked();
        }
        return build(user.getId(), courseId);
    }

    /**
     * Opening a module -> IN_PROGRESS row. Viewing its content -> percent goes up. "Mark as completed" -> 100 %.
     * A module's percent only ever goes up, and 100 % = COMPLETED.
     */
    @Transactional
    public SectionProgressDto.Response update(Long courseId, Long sectionId, String rawStatus, Integer rawPercent) {
        User user = courseAccess.currentUser();
        courseAccess.requireReadableCourse(courseId);
        int requested = requestedPercent(rawStatus, rawPercent);

        if (!isTrackedLearner(user, courseId)) {
            return untracked();
        }
        SectionEntity section = sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException("Section not found with id: " + sectionId, HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        SectionProgressEntity row = progressRepository.findByUserIdAndSectionId(user.getId(), section.getId())
                .orElse(null);
        if (row == null) {
            row = new SectionProgressEntity();
            row.setUserId(user.getId());
            row.setCourseId(courseId);
            row.setSectionId(section.getId());
            row.setStartedAt(now);
            row.setProgressPercent(0);
            row.setStatus(SectionProgressEntity.IN_PROGRESS);
            apply(row, requested, now);
            try {
                progressRepository.saveAndFlush(row);
            } catch (DataIntegrityViolationException e) {
                // two requests raced (e.g. open + view): re-read and apply on the existing row
                row = progressRepository.findByUserIdAndSectionId(user.getId(), section.getId())
                        .orElseThrow(() -> new ApiException("Could not save progress. Please try again.", HttpStatus.CONFLICT));
                apply(row, requested, now);
                progressRepository.save(row);
            }
        } else {
            apply(row, requested, now);
            progressRepository.save(row);
        }

        rollUpEnrollment(user.getId(), courseId);
        return build(user.getId(), courseId);
    }

    // ------------------------------------------------------------------

    /** Raises the row's percent (never lowers it) and sets COMPLETED at 100. */
    private void apply(SectionProgressEntity row, int requested, LocalDateTime now) {
        int current = percentOf(row);
        int next = Math.max(current, requested);
        row.setProgressPercent(next);
        if (next >= 100) {
            row.setStatus(SectionProgressEntity.COMPLETED);
            if (row.getCompletedAt() == null) row.setCompletedAt(now);
        } else {
            row.setStatus(SectionProgressEntity.IN_PROGRESS);
        }
    }

    /** COMPLETED -> 100, otherwise the sent percent, otherwise 0 (IN_PROGRESS = just opened). Nothing sent -> 400. */
    private int requestedPercent(String rawStatus, Integer rawPercent) {
        if ((rawStatus == null || rawStatus.isBlank()) && rawPercent == null) {
            throw new ApiException("Status or percent is required.", HttpStatus.BAD_REQUEST);
        }
        int percent = rawPercent == null ? 0 : Math.max(0, Math.min(100, rawPercent));
        if (rawStatus != null && !rawStatus.isBlank()) {
            String s = rawStatus.trim().toUpperCase();
            if (SectionProgressEntity.COMPLETED.equals(s)) return 100;
            if (!SectionProgressEntity.IN_PROGRESS.equals(s)) {
                throw new ApiException("Status must be IN_PROGRESS or COMPLETED.", HttpStatus.BAD_REQUEST);
            }
        }
        return percent;
    }

    /** Old rows (before percent existed) have status COMPLETED but percent 0 -> treat as 100. */
    private static int percentOf(SectionProgressEntity row) {
        if (SectionProgressEntity.COMPLETED.equals(row.getStatus())) return 100;
        return row.getProgressPercent() == null ? 0 : row.getProgressPercent();
    }

    /** Only a non-manager who is enrolled in the course has progress. Admins / instructors get tracked = false. */
    private boolean isTrackedLearner(User user, Long courseId) {
        return !courseAccess.isManager(user) && enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId());
    }

    private SectionProgressDto.Response untracked() {
        return SectionProgressDto.Response.builder()
                .tracked(false).progressPercent(0).completedCount(0).totalCount(0).sections(List.of())
                .build();
    }

    private SectionProgressDto.Response build(Long userId, Long courseId) {
        List<SectionEntity> sections = sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId);
        Map<Long, Integer> percentBySection = new HashMap<>();
        for (SectionProgressEntity p : progressRepository.findByUserIdAndCourseId(userId, courseId)) {
            percentBySection.put(p.getSectionId(), percentOf(p));
        }
        int completed = 0;
        int sum = 0;
        List<SectionProgressDto.Item> items = new java.util.ArrayList<>();
        for (SectionEntity s : sections) {
            Integer pct = percentBySection.get(s.getId());
            int percent = pct == null ? 0 : pct;
            String st = pct == null ? "NOT_STARTED"
                    : percent >= 100 ? SectionProgressEntity.COMPLETED : SectionProgressEntity.IN_PROGRESS;
            if (percent >= 100) completed++;
            sum += percent;
            items.add(SectionProgressDto.Item.builder().sectionId(s.getId()).status(st).percent(percent).build());
        }
        int total = sections.size();
        // course progress = average of the module percents
        int coursePercent = total == 0 ? 0 : (int) Math.round(sum / (double) total);
        return SectionProgressDto.Response.builder()
                .tracked(true).progressPercent(coursePercent).completedCount(completed).totalCount(total).sections(items)
                .build();
    }

    /** Keeps course_enrollments in step: progress %, Not Started / In Progress / Completed, completion date. */
    private void rollUpEnrollment(Long userId, Long courseId) {
        Optional<EnrollmentEntity> found = enrollmentRepository.findByCourseIdAndUserId(courseId, userId);
        if (found.isEmpty()) return;
        EnrollmentEntity enrollment = found.get();

        SectionProgressDto.Response r = build(userId, courseId);
        enrollment.setProgressPercent(r.getProgressPercent());
        boolean allDone = r.getTotalCount() > 0 && r.getCompletedCount() == r.getTotalCount();
        boolean anyStarted = r.getSections().stream().anyMatch(i -> !"NOT_STARTED".equals(i.getStatus()));
        if (allDone) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) enrollment.setCompletedAt(LocalDateTime.now());
        } else {
            enrollment.setStatus(anyStarted ? EnrollmentStatus.IN_PROGRESS : EnrollmentStatus.NOT_STARTED);
            enrollment.setCompletedAt(null);
        }
        enrollmentRepository.save(enrollment);
    }
}