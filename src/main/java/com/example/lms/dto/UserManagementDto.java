package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Request / response shapes of the All Users page (GET/PATCH/DELETE /api/users/...). */
public final class UserManagementDto {

    private UserManagementDto() {
    }

    /** One table row. Field names match Angular's ManagedUser exactly. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long id;
        /** "First Last" - shown in the Username column. */
        private String username;
        private String role;
        private String email;
        /** Active / Inactive / Suspended. */
        private String status;
        /** Null until an admin sets it; the UI shows "-". */
        private String department;
        /** Set while a suspension is scheduled or running (yyyy-MM-dd), else null. */
        private LocalDate suspendFrom;
        private LocalDate suspendTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResponse {
        private List<Row> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /** Body of PATCH /api/users/{id}/status. Dates are required only for "Suspended". */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusRequest {
        @NotBlank(message = "Status is required.")
        @Pattern(regexp = "Active|Inactive|Suspended",
                message = "Status must be Active, Inactive or Suspended.")
        private String status;

        private LocalDate suspendFrom;
        private LocalDate suspendTo;
    }

    /** Body of POST /api/users/bulk-delete. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDeleteRequest {
        @NotEmpty(message = "Select at least one user.")
        @Size(max = 200, message = "You can delete at most 200 users at a time.")
        private List<Long> ids;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDeleteResponse {
        private int deleted;
        /** Ids that were not deleted (not in this company / already deleted). */
        private List<Long> skipped;
    }

    /** POST /api/users/bulk-delete/import response (Excel: one email per row). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDeleteImportResult {
        /** Data rows found in the sheet (blank rows ignored). */
        private int total;
        private int deleted;
        /** Rows rejected - see {@code errors}. */
        private int failed;
        private List<UserImportDto.RowError> errors;
    }
}