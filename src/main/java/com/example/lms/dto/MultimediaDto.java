package com.example.lms.dto;

import lombok.*;

import java.time.LocalDateTime;

public class MultimediaDto {

    // Sent as the "resource" JSON part of the multipart request.
    // Validation lives in MultimediaService (same pattern as CourseService) so the
    // messages match the SRS Field List exactly.
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MultimediaRequest {
        private String name;         // Resource Name: required, 4..100
        private String description;  // Resource Description: optional, max 500
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MultimediaResponse {
        private Long id;
        private Long courseId;
        private Long sectionId;
        private String name;
        private String description;
        private String resourceType;
        private String fileUrl;
        private String originalFileName;
        private String fileExtension;
        private String contentType;
        private Long fileSize;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}