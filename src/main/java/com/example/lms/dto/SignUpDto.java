package com.example.lms.dto;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class SignUpDto {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignUpRequest {
        @NotBlank(message = "First Name is required.")
        @Pattern(regexp = "^[A-Za-z]{2,50}$", message = "First Name must contain only letters.")
        private String firstName;
        @NotBlank(message = "Last Name is required.")
        @Pattern(regexp = "^[A-Za-z]{1,50}$", message = "Last Name must contain only letters.")
        private String lastName;

        /**
         * Same documented rule as Login: leading/trailing spaces in the Email ID are
         * trimmed before validation, so the stored email is always clean and a later
         * login with or without stray spaces matches the same account.
         */
        private String email;
        @NotBlank(message = "Country Code is required")
        private String countryCode;
        @NotBlank(message = "Enter a valid phone number.")
        @Pattern(regexp = "^[0-9]{4,14}$", message = "Enter a valid phone number.")
        private String phoneNumber;
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 16, message = "Passwords must be between 8 and 16 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Passwords must include uppercase, lowercase, number, and special character."
        )
        private String password;
        @NotBlank(message = "Confirm Password is required.")
        private String confirmPassword;
        @NotNull(message = "Please accept the Terms & Conditions.")
        @AssertTrue(message = "Please accept the Terms & Conditions.")
        private Boolean acceptTerms;

        /**
         * Validation annotations live on the GETTER so they are checked against the
         * trimmed value, independent of how the JSON mapper populated the field.
         */
        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 100, message = "Enter a valid email address.")
        public String getEmail() {
            return email == null ? null : email.trim();
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignUpResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String countryCode;
        private String phoneNumber;
        private String role;
    }

    /**
     * Row shape for GET /api/auth/signup/admins. User-table fields only —
     * packageId/packageExpiresAt are passed through as-is so the frontend
     * can look up package details via PackageService (GET /api/packages/by-ids),
     * not resolved here.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminSummary {
        private Long id;
        private String username;      // firstName + lastName
        private String email;
        private String role;
        private String status;        // Active | Inactive (from User.active)
        private Long packageId;       // null if no package assigned
        private LocalDateTime packageExpiresAt;
        private LocalDateTime createdAt;
    }
}