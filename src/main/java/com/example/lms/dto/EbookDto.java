package com.example.lms.dto;

import lombok.*;

import java.time.LocalDateTime;

public class EbookDto {

    /** Body of POST / PUT: the name the instructor typed and the designed pages (a JSON array, as text). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EbookRequest {
        private String name;     // Ebook Name: required, 4..100
        private String content;  // JSON array of ebook sections (at least one)
    }

    /** Full ebook (content included) - for the editor and the reader. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EbookResponse {
        private Long id;
        private Long courseId;
        private Long sectionId;
        private String name;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /** List row (Organize Modules, content tabs) - no content. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EbookSummaryResponse {
        private Long id;
        private Long courseId;
        private Long sectionId;
        private String name;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}