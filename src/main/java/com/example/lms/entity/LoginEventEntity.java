package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ledger of successful logins - feeds the "Login's & Sign up's" chart and the "Online Users"
 * figure on the admin dashboard. Plain ids (no FK) like the other ledger tables, so history
 * survives a user delete. organization_id = the tenant (company admin id) of the user.
 */
@Entity
@Table(
        name = "login_events",
        indexes = {
                @Index(name = "idx_login_events_org_at", columnList = "organization_id, logged_in_at"),
                @Index(name = "idx_login_events_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class LoginEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "logged_in_at", nullable = false)
    private LocalDateTime loggedInAt;

    public LoginEventEntity(Long userId, Long organizationId) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.loggedInAt = LocalDateTime.now();
    }
}