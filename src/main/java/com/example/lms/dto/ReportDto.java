package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Admin > Reports. Every value here is computed on the server from real tables,
 * scoped to the caller's organization (tenant = company admin id).
 *
 * A field that is null means "the system does not record this yet" or "no data yet"
 * (e.g. a learner who never logged in). The UI shows "-" for null.
 */
public class ReportDto {

    /** One row of the Course Report = one course of the organization. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseRow {
        private Long id;
        private String courseTitle;
        /** Display code derived from the id, e.g. CRS-0012 (courses have no code column). */
        private String courseCode;
        private String category;
        /** Number of enrollments (one per learner per course). */
        private long enrollments;
        /** completed / enrollments, whole percent. 0 when nobody is enrolled. */
        private int completionRate;
        private long learners;
        private long completedLearners;
        private long learnersInProgress;
        private long learnersNotStarted;
        /** Average of enrollments.progress_percent, whole percent. 0 when nobody is enrolled. */
        private int avgUserProgress;
        /** Sum of study_time for the course, formatted like "2h 05m" / "12m". */
        private String totalTimeSpent;
        /** Rows in issued_certificates for this course. */
        private long certificatesIssued;
    }

    /** One row of the Learner Report = one learner of the organization (even with 0 enrollments). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearnerRow {
        private Long userId;
        private String learner;
        private String role;
        private String department;
        /** users.created_at */
        private LocalDateTime registrationDate;
        /** Latest content / quiz / module progress event. Null = no learning activity yet. */
        private LocalDateTime lastActivity;
        /** Latest row in login_events. Null = never logged in. */
        private LocalDateTime lastLogin;
        private long completedCourses;
        /** In Progress + Not Started enrollments. */
        private long incompleteCourses;
        private long enrolledCourses;
        /** Average of enrollments.score_percent (2 decimals). Null = no score yet. */
        private Double averageScore;
        /** Sum of study_time over the organization's courses, e.g. "3h 20m". */
        private String studyTime;
        /** users.platform_seconds, e.g. "12h 45m". */
        private String totalTimeOnPlatform;
    }
}