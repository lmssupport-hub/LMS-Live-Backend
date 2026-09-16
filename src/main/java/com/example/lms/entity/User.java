package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    public boolean isPackageExpired() {
        return packageExpiresAt != null && LocalDateTime.now().isAfter(packageExpiresAt);
    }
}