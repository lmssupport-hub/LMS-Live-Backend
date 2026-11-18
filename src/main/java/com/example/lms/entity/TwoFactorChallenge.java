package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "two_factor_challenge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "purpose"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorChallenge {

    public enum Purpose { LOGIN, ENABLE, DISABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 10)
    private Purpose purpose;

    /** SHA-256 of the challengeToken (LOGIN only; null for ENABLE / DISABLE). */
    @Column(name = "token_hash", length = 64, unique = true)
    private String tokenHash;

    /** "EMAIL" (OTP mailed, checked against otpHash) or "AUTHENTICATOR" (TOTP; otpHash is a placeholder). */
    @Column(name = "method", nullable = false, length = 20, columnDefinition = "varchar(20) default 'EMAIL'")
    @Builder.Default
    private String method = "EMAIL";

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    /** ENABLE + AUTHENTICATOR only: the encrypted secret shown in the QR code, not active until confirmed. */
    @Column(name = "pending_secret", length = 255)
    private String pendingSecret;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /** "Remember me" ticked on the login form - applied to the JWT issued after the OTP is verified. */
    @Column(name = "remember_me", nullable = false)
    @Builder.Default
    private Boolean rememberMe = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
