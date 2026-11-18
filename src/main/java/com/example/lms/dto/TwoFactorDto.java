package com.example.lms.dto;

import com.example.lms.entity.TwoFactorChallenge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TwoFactorDto {

    private static final String OTP_REGEX = "^\\d{6}$";
    private static final String OTP_MESSAGE = "Enter the 6-digit verification code.";

    /** POST /api/auth/2fa/login/verify - second step of login. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginVerifyRequest {
        @NotBlank(message = "Your login session has expired. Please log in again.")
        @Size(max = 128, message = "Your login session has expired. Please log in again.")
        private String challengeToken;

        @NotBlank(message = OTP_MESSAGE)
        @Pattern(regexp = OTP_REGEX, message = OTP_MESSAGE)
        private String otp;
    }

    /** POST /api/auth/2fa/login/resend */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResendRequest {
        @NotBlank(message = "Your login session has expired. Please log in again.")
        @Size(max = 128, message = "Your login session has expired. Please log in again.")
        private String challengeToken;
    }

    /** POST /api/profile/2fa/send-otp - ENABLE or DISABLE (LOGIN is not allowed here). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendOtpRequest {
        @NotNull(message = "Action is required.")
        private TwoFactorChallenge.Purpose action;
    }

    /** POST /api/profile/2fa/enable and /disable */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpRequest {
        @NotBlank(message = OTP_MESSAGE)
        @Pattern(regexp = OTP_REGEX, message = OTP_MESSAGE)
        private String otp;
    }

    /** POST /api/profile/2fa/authenticator/setup - what the QR code / "enter key manually" needs. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuthenticatorSetup {
        /** Base32 key to type in manually. */
        private String secret;
        /** otpauth:// URI - the browser turns this into the QR code. */
        private String otpauthUri;
    }

    /** GET /api/profile/2fa */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Status {
        private boolean enabled;
        /** "EMAIL", "AUTHENTICATOR" or null when 2FA is off. */
        private String method;
        /** Where the code is sent, e.g. j***@gmail.com. */
        private String maskedEmail;
    }
}
