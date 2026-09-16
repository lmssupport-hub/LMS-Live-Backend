package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class InviteDto {

    /** POST /api/invites body - what the admin submits from the Invite People modal. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SendRequest {
        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        private String email;

        @NotNull(message = "Please select a role.")
        private Long roleId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendResponse {
        private String email;
        private String roleName;
        private LocalDateTime expiresAt;
    }

    /** GET /api/invites/{token} response - lets the signup page pre-fill email + show the role being granted. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteDetails {
        private String email;
        private String roleName;
        private LocalDateTime expiresAt;
    }

    /** POST /api/invites/{token}/accept body - same as normal signup minus email (locked to the invite). */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AcceptRequest {
        @NotBlank(message = "First Name is required.")
        private String firstName;

        @NotBlank(message = "Last Name is required.")
        private String lastName;

        @NotBlank(message = "Country Code is required")
        private String countryCode;

        @NotBlank(message = "Enter a valid phone number.")
        private String phoneNumber;

        @NotBlank(message = "Password is required.")
        private String password;

        @NotBlank(message = "Confirm Password is required.")
        private String confirmPassword;

        @NotNull(message = "Please accept the Terms & Conditions.")
        private Boolean acceptTerms;
    }
}