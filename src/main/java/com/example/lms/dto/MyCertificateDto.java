package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** What a learner sees on "My Certificate". */
public class MyCertificateDto {

    /** One row of the list (Course Name | Issue date). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private Long id;
        private Long courseId;
        private String courseName;
        private String certificateNumber;
        private LocalDateTime issuedAt;
    }

    /**
     * Everything needed to draw one certificate: the values for the {{tags}} plus the organization's design.
     * template = null when the admin never saved a design (the screen then uses its default design).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private Long id;
        private Long courseId;
        private String courseName;
        private String learnerName;
        private String organizationName;
        private String certificateNumber;
        private LocalDateTime issuedAt;
        private CertificateTemplateDto.Response template;
    }
}