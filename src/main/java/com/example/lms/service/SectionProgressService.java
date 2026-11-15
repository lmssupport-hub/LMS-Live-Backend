package com.example.lms.service;

import com.example.lms.dto.SectionProgressDto;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.SectionEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.MultimediaRepository;
import com.example.lms.repository.SectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Learner progress per course section (shown as rings on Course Preview) and its roll-up into
 * course_enrollments.progress_percent / status.
 *
 * Section percent = average of its items (every multimedia file + the quiz). A multimedia item counts only as far
 * as the learner really got (100 % = completed); the quiz counts 0 until it is submitted.
 */
@Service
public class SectionProgressService {

    private final CourseAccessService courseAccess;
    private final SectionRepository sectionRepository;
    private final MultimediaRepository multimediaRepository;
    private final ContentProgressService contentProgress;
    private final EnrollmentRepository enrollmentRepository;

    public SectionProgressService(CourseAccessService courseAccess,
                                  SectionRepository sectionRepository,
                                  MultimediaRepository multimediaRepository,
                                  ContentProgressService contentProgress,
                                  EnrollmentRepository enrollmentRepository) {
        this.courseAccess = courseAccess;
        this.sectionRepository = sectionRepository;
        this.multimediaRepository = multimediaRepository;
        this.contentProgress = contentProgress;
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

    /** The learner has reached `percent` % of one multimedia item. Only ever raised; 100 = completed. */
    @Transactional
    public SectionProgressDto.Response updateItem(Long courseId, Long sectionId, Long itemId, Integer percent) {
        User user = courseAccess.currentUser();
        courseAccess.requireReadableCourse(courseId);
        if (!isTrackedLearner(user, courseId)) {
            return untracked();
        }
        sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException("Section not found with id: " + sectionId, HttpStatus.NOT_FOUND));
        if (isSectionLocked(user.getId(), courseId, sectionId)) {
            throw new ApiException("Complete the previous module first.", HttpStatus.FORBIDDEN);
        }
        multimediaRepository.findByIdAndSectionId(itemId, sectionId)
                .orElseThrow(() -> new ApiException("Content not found with id: " + itemId, HttpStatus.NOT_FOUND));

        contentProgress.record(user.getId(), courseId, sectionId, ContentProgressService.MULTIMEDIA, itemId,
                percent == null ? 0 : percent);
        rollUpEnrollment(user.getId(), courseId);
        return build(user.getId(), courseId);
    }

    /** A module opens only when the previous module (that has content) is completed. */
    @Transactional(readOnly = true)
    public boolean isSectionLocked(Long userId, Long courseId, Long sectionId) {
        return build(userId, courseId).getSections().stream()
                .anyMatch(s -> s.getSectionId().equals(sectionId) && s.isLocked());
    }

    /** Called after the quiz is submitted: the quiz item changed, so the course percent / status may change. */
    @Transactional
    public void refreshEnrollment(Long userId, Long courseId) {
        rollUpEnrollment(userId, courseId);
    }

    // ------------------------------------------------------------------

    /** Only a non-manager who is enrolled in the course has progress. Admins / instructors get tracked = false. */
    private boolean isTrackedLearner(User user, Long courseId) {
        return !courseAccess.isManager(user) && enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId());
    }

    private SectionProgressDto.Response untracked() {
        return SectionProgressDto.Response.builder()
                .tracked(false).progressPercent(0).completedCount(0).totalCount(0)
                .sections(List.of()).items(List.of())
                .build();
    }

    private SectionProgressDto.Response build(Long userId, Long courseId) {
        List<SectionEntity> sections = sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId);
        Map<String, Integer> stored = contentProgress.storedPercents(userId, courseId);

        List<SectionProgressDto.Item> sectionViews = new ArrayList<>();
        List<SectionProgressDto.ItemView> allItems = new ArrayList<>();
        boolean blocked = false; // an earlier module is not completed -> every later module is locked
        int counted = 0;      // sections that have something to learn
        int completed = 0;
        int sumPercent = 0;
        for (SectionEntity section : sections) {
            List<SectionProgressDto.ItemView> items = contentProgress.itemsOfSection(section.getId(), stored);
            allItems.addAll(items);
            int percent = 0;
            if (!items.isEmpty()) {
                int sum = items.stream().mapToInt(SectionProgressDto.ItemView::getPercent).sum();
                percent = (int) Math.round(sum / (double) items.size());
                counted++;
                sumPercent += percent;
                if (percent >= 100) completed++;
            }
            String status = percent >= 100 ? "COMPLETED" : percent > 0 ? "IN_PROGRESS" : "NOT_STARTED";
            sectionViews.add(SectionProgressDto.Item.builder()
                    .sectionId(section.getId()).status(status).percent(percent).locked(blocked).build());
            if (!items.isEmpty() && percent < 100) blocked = true;
        }
        int coursePercent = counted == 0 ? 0 : (int) Math.round(sumPercent / (double) counted);
        return SectionProgressDto.Response.builder()
                .tracked(true).progressPercent(coursePercent).completedCount(completed).totalCount(counted)
                .sections(sectionViews).items(allItems)
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
        boolean anyStarted = r.getItems().stream().anyMatch(i -> i.getPercent() > 0);
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