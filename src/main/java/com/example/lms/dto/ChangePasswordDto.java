package com.example.lms.dto;

import com.example.lms.util.PasswordPolicy;
import jakarta.validation.constraints.*;
import lombok.*;

public class ChangePasswordDto {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current Password is required.")
        @Size(max = 128, message = "Current Password is invalid.")
        private String currentPassword;

        @NotBlank(message = "New Password is required.")
        @Size(min = PasswordPolicy.MIN, max = PasswordPolicy.MAX,
              message = "Passwords must be between 8 and 16 characters.")
        @Pattern(regexp = PasswordPolicy.STRONG_REGEX,
              message = "Passwords must include uppercase, lowercase, number, and special character.")
        private String newPassword;

        @NotBlank(message = "Confirm Password is required.")
        private String confirmPassword;
    }
}