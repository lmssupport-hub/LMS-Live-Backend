package com.example.lms.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Learner-side view of a Question Bank. IMPORTANT: it never carries the correct flags, the
 * expected answer, the scores or the feedback - those stay on the server.
 */
public class QuizAttemptDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionView {
        private Long id;
        private String text;
    }

    /** The learner's saved answer. Empty lists / null = not answered. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerView {
        private List<Long> optionIds;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionView {
        private Long id;
        private String text;
        /** SINGLE_ANSWER | MULTIPLE_ANSWER | SHORT_TEXT */
        private String type;
        private List<OptionView> options;
        private AnswerView answer;
    }

    /** A "Question Section" of the bank (step 1, 2, 3... of the Sections bar). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionView {
        private Long id;
        private String name;
        /** True after "Submit Section": answers of this section are locked. */
        private boolean submitted;
        private List<QuestionView> questions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptResponse {
        /** Null while NOT_STARTED. */
        private Long attemptId;
        private String bankName;
        private String courseName;
        /** Course thumbnail for the "Ready to take the quiz?" page (may be null). */
        private String thumbnailUrl;
        /** NOT_STARTED | IN_PROGRESS | SUBMITTED */
        private String status;
        /** 0 while NOT_STARTED, otherwise 1, 2, 3... */
        private int attemptNumber;
        private int maxAttempts;
        /** The next unit (course section) of the course, for "Go to next unit". Null on the last unit. */
        private Long nextSectionId;
        /** When the quiz was finished (SUBMITTED only). */
        private LocalDateTime submittedAt;
        /** Seconds since the attempt started (frozen once submitted). The timer counts up from here. */
        private long elapsedSeconds;
        /** Only after the attempt is submitted. */
        private Integer score;
        private Integer maxScore;
        private List<SectionView> sections;
    }

    // ---------- answer review (Score page: Correct Answer - View / Incorrect Answer - View) ----------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewOption {
        private Long id;
        private String text;
        /** Is this option a correct one? */
        private boolean correct;
        /** Did the learner pick it? */
        private boolean selected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewQuestion {
        private Long id;
        private String sectionName;
        private String text;
        private String type;
        /** True only when the learner answered it fully right. */
        private boolean correct;
        private boolean answered;
        private List<ReviewOption> options;
        /** Short text: what the learner typed / the expected answer. */
        private String givenText;
        private String expectedAnswer;
        /** The instructor's feedback for a right / wrong answer (may be null). */
        private String feedback;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewResponse {
        private int correctCount;
        private int incorrectCount;
        private List<ReviewQuestion> questions;
    }

    /** Body of PUT .../attempt/answers/{questionId}. Empty optionIds and blank text = clear the answer. */
    @Data
    @NoArgsConstructor
    public static class SaveAnswerRequest {
        private List<Long> optionIds;

        @Size(max = 500, message = "Answer must be at most 500 characters.")
        private String text;
    }
}