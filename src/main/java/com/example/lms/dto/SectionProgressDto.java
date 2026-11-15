package com.example.lms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class SectionProgressDto {

    /** Body of POST /api/courses/{courseId}/sections/{sectionId}/items/multimedia/{itemId}/progress. */
    @Data
    @NoArgsConstructor
    public static class UpdateRequest {
        /** How far the learner has got in this item, 0-100. Only ever raised. 100 = completed. */
        @NotNull(message = "Percent is required.")
        @Min(value = 0, message = "Percent must be between 0 and 100.")
        @Max(value = 100, message = "Percent must be between 0 and 100.")
        private Integer percent;
    }

    /** Progress of one course section (module): average of its items. Drives the rings on Course Preview. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long sectionId;
        /** NOT_STARTED | IN_PROGRESS | COMPLETED */
        private String status;
        /** 0-100 */
        private int percent;
        /** True while the previous module is not completed yet: this module can't be opened. The first module is never locked. */
        private boolean locked;
    }

    /** One content item (multimedia file / quiz) of a section with the learner's progress. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemView {
        private Long sectionId;
        /** MULTIMEDIA | QUIZ */
        private String itemType;
        private Long itemId;
        private String name;
        /** 0-100; 100 = completed (quiz: submitted). */
        private int percent;
    }

    /**
     * tracked = false for admins / instructors (they are not learners of the course): the screen then
     * shows no progress rings and no locks. tracked = true for an enrolled learner.
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
        private List<ItemView> items;
    }
}