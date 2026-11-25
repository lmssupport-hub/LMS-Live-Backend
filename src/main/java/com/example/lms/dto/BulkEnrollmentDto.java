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

    /** POST /api/users/bulk-enrollment/import response (Excel: create missing users + enroll). */
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImportResult {
        /** Data rows found in the sheet (blank rows ignored). */
        private int total;
        /** New users created because their email was not registered yet. */
        private int usersCreated;
        /** New enrollments saved. */
        private int enrolled;
        /** Rows whose user was already enrolled in that course (nothing changed, not an error). */
        private int alreadyEnrolled;
        /** Rows rejected - see {@code errors}. */
        private int failed;
        /** Newly created users whose "set your password" mail could not be sent. */
        private int emailsFailed;
        private List<UserImportDto.RowError> errors;
    }

    /** POST /api/users/bulk-enrollment/unenroll-import response (Excel: email + course per row). */
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UnenrollImportResult {
        /** Data rows found in the sheet (blank rows ignored). */
        private int total;
        /** Enrollments removed. */
        private int unenrolled;
        /** Rows rejected - see {@code errors}. */
        private int failed;
        private List<UserImportDto.RowError> errors;
    }
}