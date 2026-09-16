package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "invites", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
@Getter
@Setter
@NoArgsConstructor
public class InviteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    // The Role (from Role Management) this invited person will get once they sign up.
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    // Denormalized so the signup pre-fill screen doesn't need a join, and
    // stays correct even if the Role is later renamed.
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    // Which admin (company) sent this invite - the invited user's account is
    // scoped to THIS admin (see User.companyAdminId), not whoever is logged
    // in when they eventually sign up (they aren't logged in at all yet).
    @Column(name = "invited_by_admin_id", nullable = false)
    private Long invitedByAdminId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    // PENDING / ACCEPTED / EXPIRED / REVOKED
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

    @Transient
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}