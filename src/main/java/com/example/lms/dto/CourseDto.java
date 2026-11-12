package com.example.lms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CourseDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CourseRequest {

        @NotBlank(message = "Course Name is required.")
        @Size(min = 3, max = 100, message = "Course Name must be between 3 and 100 characters.")
        private String name;

        @Size(max = 1000, message = "Course Description must not exceed 1000 characters")
        private String description;

        @NotNull(message = "Course Category is required")
        private Integer categoryId;

        @NotNull(message = "Instructor selection is required")
        private Long instructorId;

        @NotBlank(message = "Course Level is required")
        private String level;       // BEGINNER / INTERMEDIATE / ADVANCED

        @NotBlank(message = "Course Status is required")
        private String status;      // DRAFT / PUBLISHED / ARCHIVED

        // NEW: Free / Paid toggle + price in USD
        @NotNull(message = "Please select Free or Paid.")
        private Boolean paid;

        @DecimalMin(value = "0.0", message = "Price must not be negative.")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places.")
        private BigDecimal price;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CourseResponse {
        private Long id;
        private String name;
        private String description;
        private Integer categoryId;
        private String categoryName;
        private Long instructorId;
        private String instructorName;
        private String level;
        private String status;
        private String thumbnailUrl;
        private Boolean paid;
        private BigDecimal price;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /** NEW: learner view only - true when the learner is enrolled (Start course), false otherwise (Enroll). Null for managers. */
        private Boolean enrolled;
        /** NEW: learner view only - NOT_STARTED / IN_PROGRESS / COMPLETED when enrolled, otherwise null. */
        private String enrollmentStatus;
    }
}