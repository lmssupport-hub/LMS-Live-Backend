package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Learner dashboard home. Every number is computed on the server for the LOGGED-IN learner only.
 * The field names match the LearnerDashboardData interface of the Angular dashboard, so the screen
 * just shows them.
 */
public class LearnerDashboardDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** Quizzes (question banks) the learner submitted with a score of at least PASS_PERCENT. */
        private long passedQuizzes;
        /** Pass mark used for passedQuizzes, so the screen can tell the learner (the LMS has no per-quiz pass mark). */
        private int passPercent;
        private long enrolledCourses;
        private long completedCourses;
        private long inProgressCourses;
        private long notStartedCourses;
        private CourseStatus courseStatus;
        /** The learner's enrolled courses (names under "Training Analytics"). */
        private List<CourseRef> quizCourses;
        /** One entry per submitted quiz, oldest first. */
        private List<QuizScore> quizScores;
        /** Active study minutes per course (only courses with time), A-Z by course name. */
        private List<TrainingRecord> trainingTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CourseStatus {
        private long completed;
        private long inProgress;
        private long notStarted;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CourseRef {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuizScore {
        private Long courseId;
        private String courseName;
        private String quizName;
        private int score;
        private int maxScore;
        /** 0-100, whole percent. */
        private int percentage;
        private boolean passed;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrainingRecord {
        private Long courseId;
        private String courseName;
        /** Study time from StudyTimeService (pings while the course page is open), rounded to minutes, min 1. */
        private long totalMinutes;
    }
}