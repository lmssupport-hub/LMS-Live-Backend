package com.example.lms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class SectionProgressDto {

    /**
     * Body of POST /api/courses/{courseId}/sections/{sectionId}/progress.
     *  - status = COMPLETED ("Mark as completed")  -> 100 %
     *  - status = IN_PROGRESS (module opened)       -> row created, percent unchanged
     *  - percent = 0-100 (share of the module's content viewed) -> raised, never lowered
     * At least one of the two must be sent.
     */
    @Data
    @NoArgsConstructor
    public static class UpdateRequest {
        private String status;

        @Min(value = 0, message = "Percent must be between 0 and 100.")
        @Max(value = 100, message = "Percent must be between 0 and 100.")
        private Integer percent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long sectionId;
        /** NOT_STARTED | IN_PROGRESS | COMPLETED */
        private String status;
        /** 0-100, drives how much of the ring is filled. */
        private int percent;
    }

    /**
     * tracked = false for admins / instructors (they are not learners of the course): the screen then
     * shows no progress rings. tracked = true for an enrolled learner.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private boolean tracked;
        private int progressPercent;
        private int completedCount;
        private int totalCount;
        private List<Item> sections;
    }
}