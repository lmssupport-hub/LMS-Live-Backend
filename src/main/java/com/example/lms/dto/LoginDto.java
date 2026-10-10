package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class LoginDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginRequest {

        /**
         * Input-handling rule (documented, applies to Login AND Sign Up):
         * leading/trailing whitespace in the Email ID is TRIMMED before validation
         * and authentication, so " user@example.com " logs in exactly like
         * "user@example.com". A value that is only spaces becomes empty and fails
         * with "Email ID is required".
         *
         * The password is NEVER trimmed - spaces can be a legitimate part of a password.
         */
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 16, message = "Invalid Email ID or Password")
        private String password;

        // Optional field from the Field List - not mandatory.
        // When true, LoginService issues a longer-lived JWT (see JwtUtil).
        private Boolean rememberMe;

        /**
         * The validation annotations are on the GETTER on purpose: Hibernate Validator then
         * validates the value returned here (already trimmed), independent of how the JSON
         * mapper (Jackson 2 / 3) populated the field.
         */
        @NotBlank(message = "Email ID is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Enter a valid email address")
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
    public static class LoginResponse {
        private String token;
        private String tokenType;
        private Long userId;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
    }
}