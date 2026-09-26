package com.example.lms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}