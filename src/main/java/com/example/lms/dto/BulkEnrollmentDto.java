package com.example.lms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** All Users > Bulk Users > Enroll in course / Unenroll from course. */
public class BulkEnrollmentDto {

    /** One option of the course dropdown. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CourseOption {
        private Long id;
        private String name;
        /** DRAFT / PUBLISHED */
        private String status;
    }

    /** Body of POST /enroll and POST /unenroll: the users ticked in the All Users table. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotNull(message = "Please select a course.")
        private Long courseId;

        @NotNull(message = "Please select at least one user")
        @Size(min = 1, message = "Please select at least one user")
        private List<@NotNull(message = "User is required.") Long> userIds;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Skipped {
        private Long userId;
        /** null when the id does not belong to this company. */
        private String name;
        private String reason;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Result {
        private Long courseId;
        private String courseName;
        private int requested;
        /** Users actually enrolled / unenrolled. */
        private int affected;
        private List<Skipped> skipped;
    }
}