package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class UserImportDto {

    /** POST /api/users/import response. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        /** Data rows found in the sheet (blank rows ignored). */
        private int total;
        /** Rows saved into the users table. */
        private int imported;
        /** Rows rejected - see {@code errors}. */
        private int failed;
        /** Imported users whose "set your password" mail could not be sent (they can use Forgot password). */
        private int emailsFailed;
        private List<RowError> errors;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        /** Excel row number (header = 1, first data row = 2). */
        private int row;
        private String email;
        private String reason;
    }
}