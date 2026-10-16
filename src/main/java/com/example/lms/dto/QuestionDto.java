package com.example.lms.dto;

import com.example.lms.entity.QuestionType;
import com.example.lms.entity.ScoringType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class QuestionDto {

    // type / scoringType are Strings on purpose: an unknown value becomes a clean 400
    // from QuestionValidator instead of a Jackson deserialization error.

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OptionRequest {

        @NotBlank(message = "Option text is required.")
        @Size(max = 200, message = "Option must not exceed 200 characters.")
        private String text;

        private boolean correct;

        @Min(value = 0, message = "Score must be between 0 and 100.")
        @Max(value = 100, message = "Score must be between 0 and 100.")
        private Integer score;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionRequest {

        @NotBlank(message = "Question is required.")
        @Size(max = 500, message = "Question must not exceed 500 characters.")
        private String text;

        @NotBlank(message = "Question Type is required.")
        private String type;                 // SINGLE_ANSWER / MULTIPLE_ANSWER / SHORT_TEXT

        private String scoringType;          // BASIC (default) / ADVANCED

        @Min(value = 0, message = "Score must be between 0 and 100.")
        @Max(value = 100, message = "Score must be between 0 and 100.")
        private Integer correctScore;

        @Min(value = 0, message = "Score must be between 0 and 100.")
        @Max(value = 100, message = "Score must be between 0 and 100.")
        private Integer incorrectScore;

        @Size(max = 500, message = "Feedback must not exceed 500 characters.")
        private String correctFeedback;

        @Size(max = 500, message = "Feedback must not exceed 500 characters.")
        private String incorrectFeedback;

        @Size(max = 500, message = "Answer must not exceed 500 characters.")
        private String expectedAnswer;       // SHORT_TEXT only

        @Valid
        @Size(max = 20, message = "A question can have at most 20 options.")
        private List<OptionRequest> options;

        /** Update only: version the client last loaded. Mismatch => 409. */
        private Long version;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OptionResponse {
        private Long id;
        private String text;
        private boolean correct;
        private int score;
        private int order;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionResponse {
        private Long id;
        private Long sectionId;
        private String text;
        private QuestionType type;
        private ScoringType scoringType;
        private int correctScore;
        private int incorrectScore;
        private String correctFeedback;
        private String incorrectFeedback;
        private String expectedAnswer;
        private boolean hidden;
        private int questionOrder;
        private Long version;
        private List<OptionResponse> options;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ReorderRequest {
        @NotEmpty(message = "Reorder items list cannot be empty.")
        @Valid
        private List<ReorderItem> items;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ReorderItem {
        @NotNull(message = "questionId is required.")
        private Long questionId;

        @NotNull(message = "newOrder is required.")
        @Min(value = 1, message = "newOrder must be 1 or greater.")
        private Integer newOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class TargetSectionRequest {
        @NotNull(message = "targetSectionId is required.")
        private Long targetSectionId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class VisibilityRequest {
        @NotNull(message = "hidden is required.")
        private Boolean hidden;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AddExistingRequest {
        @NotEmpty(message = "Select at least one question.")
        @Size(max = 100, message = "You can add at most 100 questions at a time.")
        private List<@NotNull(message = "questionId is required.") Long> questionIds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExistingQuestionsResponse {
        private List<QuestionResponse> items;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImportResponse {
        private int imported;
    }
}