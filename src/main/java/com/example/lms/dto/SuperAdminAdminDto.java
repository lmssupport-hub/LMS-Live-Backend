package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Response shapes of Super Admin > Admins (/api/super-admin/admins). */
public final class SuperAdminAdminDto {

    private SuperAdminAdminDto() {
    }

    /** One table row. Field names match Angular's ManagedAdmin exactly. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long id;
        private String username;
        private String email;
        /** Null until the organization name is filled in on the admin's profile. */
        private String organization;
        private String profilePictureUrl;
        /** Active / Inactive / Suspended. */
        private String status;
        /** Set while a suspension is scheduled or running (yyyy-MM-dd), else null. */
        private LocalDate suspendFrom;
        private LocalDate suspendTo;
    }

    /** POST /api/super-admin/admins/import response. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResult {
        private int total;
        private int imported;
        private int failed;
        /** Imported admins whose "set your password" mail could not be sent. */
        private int emailsFailed;
        /** Imported admins with no package: they cannot log in until one is assigned. */
        private int withoutPackage;
        private List<UserImportDto.RowError> errors;
    }

    /** POST /api/super-admin/admins/export body. Empty / null ids = every admin. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportRequest {
        private List<Long> ids;
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
}