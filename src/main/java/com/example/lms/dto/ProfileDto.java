package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ProfileDto {

    /**
     * Body of PUT /api/profile/me. Field names match the Angular form control names exactly.
     * First name, last name, email, country code and phone number follow the SAME rules as signup.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @NotBlank(message = "First Name is required.")
        @Size(min = 2, max = 50, message = "First Name must be between 2 and 50 characters.")
        @Pattern(regexp = "^[A-Za-z]*$", message = "First Name must contain only letters.")
        private String firstName;

        @NotBlank(message = "Last Name is required.")
        @Size(max = 50, message = "Last Name must not exceed 50 characters.")
        @Pattern(regexp = "^[A-Za-z]*$", message = "Last Name must contain only letters.")
        private String lastName;

        private String email;

        // ---- Organization fields (admin / super admin / instructor; ignored for everyone else) ----
        /** Text, numbers and special characters allowed. */
        @Size(max = 500, message = "Organization Name must not exceed 500 characters.")
        private String organizationName;

        @Size(max = 500, message = "Organization Address must not exceed 500 characters.")
        private String organizationAddress;

        // ---- Common fields ----
        @NotBlank(message = "Country Code is required.")
        @Size(max = 5, message = "Country Code is invalid.")
        private String countryCode;

        @Size(max = 64, message = "Time Zone is invalid.")
        private String timeZone;

        @Size(max = 500, message = "Personal Address must not exceed 500 characters.")
        private String personalAddress;

        @Size(max = 100, message = "Country must not exceed 100 characters.")
        private String country;

        @Size(max = 100, message = "State must not exceed 100 characters.")
        private String state;

        @Size(max = 100, message = "City must not exceed 100 characters.")
        private String city;

        @Size(max = 20, message = "Postal Code must not exceed 20 characters.")
        private String postalCode;

        @NotBlank(message = "Phone Number is required.")
        @Pattern(regexp = "^[0-9]*$", message = "Enter a valid phone number.")
        @Size(max = 14, message = "Enter a valid phone number.")
        private String phoneNumber;

        /** Same trick as SignUpRequest: validate the trimmed value. */
        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 100, message = "Email ID must not exceed 100 characters.")
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
    public static class Response {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
        private boolean active;

        private String organizationName;
        private String organizationAddress;
        private String personalAddress;

        private String timeZone;
        private String country;
        private String state;
        private String city;
        private String postalCode;
        private String countryCode;
        private String phoneNumber;

        /** Relative paths like /uploads/profile/xxx.jpg (or null). */
        private String profilePictureUrl;
        private String backgroundPictureUrl;
    }
}