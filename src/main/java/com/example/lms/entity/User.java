package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode;

    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN";

    @Column(name = "accepted_terms", nullable = false)
    private Boolean acceptedTerms;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_assigned_at")
    private LocalDateTime packageAssignedAt;

    @Column(name = "package_expires_at")
    private LocalDateTime packageExpiresAt;

    @Column(name = "failed_login_attempts", nullable = false,
            columnDefinition = "integer not null default 0")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // Dashboard "Online Users": true after login, false after logout (POST /api/auth/logout).
    // columnDefinition has a default so ddl-auto=update can add the column to a table that already has rows.
    @Column(name = "online", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean online = false;

    // Last login / heartbeat (POST /api/auth/heartbeat every ~45 s). online = true with no heartbeat for
    // 2 minutes (browser closed without logout) no longer counts as online.
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // Total seconds the user has had the app open while logged in (Learner Report "Total Time on Platform").
    // Added by the server from the gap between two heartbeats (LoginService.heartbeat).
    // columnDefinition has a default so ddl-auto=update can add the column to a table that already has rows.
    @Column(name = "platform_seconds", nullable = false, columnDefinition = "bigint not null default 0")
    @Builder.Default
    private Long platformSeconds = 0L;

    // NEW - set only for team members created via an invite (InviteEntity /
    // InviteService). Points at the ADMIN who invited them. This is how
    // multi-company scoping works: an admin's team = every User whose
    // companyAdminId == that admin's own id. Null for the admin's own
    // self-signup account (SignUpService).
    @Column(name = "company_admin_id")
    private Long companyAdminId;

    // NEW - FK to RoleEntity.id, the custom Role (Role Management) granted
    // at invite time. `role` (String) is kept in sync with RoleEntity.name
    // so existing @PreAuthorize("hasRole(...)") checks keep working as-is;
    // roleId is for resolving the fine-grained permission set via RoleService.
    @Column(name = "role_id")
    private Long roleId;

    // 2FA (Profile > Settings > 2FA Verification Method). Only EMAIL is supported for now.
    // columnDefinition has a default so ddl-auto=update can add the columns to a table that already has rows.
    @Column(name = "two_fa_enabled", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean twoFaEnabled = false;

    /** "EMAIL" or "AUTHENTICATOR"; null while 2FA is off. */
    @Column(name = "two_fa_method", length = 20)
    private String twoFaMethod;

    /** AES-GCM encrypted Base32 TOTP secret (AUTHENTICATOR only) - see SecretCipher. */
    @Column(name = "two_fa_secret", length = 255)
    private String twoFaSecret;

    /** Last accepted TOTP time step - a code from this step or earlier is rejected (replay protection). */
    @Column(name = "two_fa_last_step")
    private Long twoFaLastStep;

    // User Management (All Users page) -------------------------------------------------
    /** Free-text department shown in the All Users table. Null until an admin sets it. */
    @Column(name = "department", length = 100)
    private String department;

    /**
     * Temporary suspension window (inclusive). While today is inside it the user cannot log in and
     * existing sessions are rejected, but {@code active} stays true so the account is untouched
     * afterwards. Both null = not suspended.
     */
    @Column(name = "suspended_from")
    private LocalDate suspendedFrom;

    @Column(name = "suspended_to")
    private LocalDate suspendedTo;

    /**
     * Soft delete marker (All Users > Delete). Rows are kept so payments, certificates and progress
     * stay intact; the email is anonymised so the address can be invited again.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.role == null) {
            this.role = "ADMIN";
        }
        if (this.failedLoginAttempts == null) {
            this.failedLoginAttempts = 0;
        }
        if (this.twoFaEnabled == null) {
            this.twoFaEnabled = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    @Transient
    public boolean isTwoFaActive() {
        return Boolean.TRUE.equals(twoFaEnabled);
    }

    @Transient
    public boolean isPackageExpired() {
        return packageExpiresAt != null && LocalDateTime.now().isAfter(packageExpiresAt);
    }

    /** True while today is inside the suspension window. */
    @Transient
    public boolean isSuspendedOn(LocalDate today) {
        return suspendedFrom != null && suspendedTo != null
                && !today.isBefore(suspendedFrom) && !today.isAfter(suspendedTo);
    }

    @Transient
    public boolean isDeleted() {
        return deletedAt != null;
    }
}