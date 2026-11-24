package com.example.lms.service;

public interface PasswordResetEmailService {

    void sendResetLinkEmail(String toEmail, String resetLink, int expiryMinutes);

    void sendInviteEmail(String toEmail, String signupLink, String roleName, int expiryMinutes);

    /** Bulk import: tells the new user an account exists and lets them set their own password. */
    void sendWelcomeSetPasswordEmail(String toEmail, String setPasswordLink, String roleName, int expiryMinutes);

    /** 2FA one-time code. {@code actionLabel} is shown in the mail, e.g. "log in to your account". */
    void sendOtpEmail(String toEmail, String otp, String actionLabel, int expiryMinutes);
}