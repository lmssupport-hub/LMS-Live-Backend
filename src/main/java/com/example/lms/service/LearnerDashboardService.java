package com.example.lms.service;

import com.example.lms.dto.LearnerDashboardDto;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.User;
import com.example.lms.repository.LearnerDashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Learner dashboard home: course counts, doughnut, quiz scores and study time of the logged-in learner.
 * Organization = PackageService.resolveOwnerId(user), the same tenant rule as everywhere else.
 */
@Service
@RequiredArgsConstructor
public class LearnerDashboardService {

    /**
     * The LMS stores a score per quiz but no pass mark, so "Passed Quizzes" uses this one number.
     * Change it here if the business wants another pass mark.
     */
    static final int PASS_PERCENT = 50;

    private final CourseAccessService courseAccess;
    private final LearnerDashboardRepository queries;

    @Transactional(readOnly = true)
    public LearnerDashboardDto.Response getDashboard() {
        User user = courseAccess.currentUser();
        Long userId = user.getId();
        Long orgId = PackageService.resolveOwnerId(user);

        // ---- course counts (enrollments of this learner)
        long completed = 0;
        long inProgress = 0;
        long notStarted = 0;
        for (Object[] r : queries.enrollmentCountsByStatus(userId, orgId)) {
            long count = ((Number) r[1]).longValue();
            if (r[0] == EnrollmentStatus.COMPLETED) {
                completed = count;
            } else if (r[0] == EnrollmentStatus.IN_PROGRESS) {
                inProgress = count;
            } else if (r[0] == EnrollmentStatus.NOT_STARTED) {
                notStarted = count;
            }
        }
        long enrolled = completed + inProgress + notStarted;

        List<LearnerDashboardDto.CourseRef> courses = new ArrayList<>();
        for (Object[] r : queries.enrolledCourses(userId, orgId)) {
            courses.add(new LearnerDashboardDto.CourseRef(((Number) r[0]).longValue(), (String) r[1]));
        }

        // ---- quiz scores
        List<LearnerDashboardDto.QuizScore> scores = new ArrayList<>();
        long passed = 0;
        for (Object[] r : queries.submittedQuizScores(userId, orgId)) {
            int score = ((Number) r[3]).intValue();
            int max = ((Number) r[4]).intValue();
            int percentage = (int) Math.round(Math.min(100.0, Math.max(0.0, score * 100.0 / max)));
            boolean isPassed = score * 100L >= (long) max * PASS_PERCENT;
            if (isPassed) {
                passed++;
            }
            scores.add(LearnerDashboardDto.QuizScore.builder()
                    .courseId(((Number) r[0]).longValue())
                    .courseName((String) r[1])
                    .quizName((String) r[2])
                    .score(score)
                    .maxScore(max)
                    .percentage(percentage)
                    .passed(isPassed)
                    .build());
        }

        // ---- study time per course
        List<LearnerDashboardDto.TrainingRecord> training = new ArrayList<>();
        for (Object[] r : queries.studySecondsPerCourse(userId, orgId)) {
            long seconds = ((Number) r[2]).longValue();
            training.add(new LearnerDashboardDto.TrainingRecord(
                    ((Number) r[0]).longValue(), (String) r[1], Math.max(1, Math.round(seconds / 60.0))));
        }

        return LearnerDashboardDto.Response.builder()
                .passedQuizzes(passed)
                .passPercent(PASS_PERCENT)
                .enrolledCourses(enrolled)
                .completedCourses(completed)
                .inProgressCourses(inProgress)
                .notStartedCourses(notStarted)
                .courseStatus(new LearnerDashboardDto.CourseStatus(completed, inProgress, notStarted))
                .quizCourses(courses)
                .quizScores(scores)
                .trainingTime(training)
                .build();
    }
}