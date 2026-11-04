package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Roles are owned by a company admin (tenant). created_by_admin_id = the tenant owner
 * (the company admin's user id, even when a team member creates the role).
 * Role names are unique PER OWNER, not system-wide.
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_roles_owner_name",
                columnNames = {"created_by_admin_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "permissions_json", columnDefinition = "TEXT")
    private String permissionsJson;

    /** Tenant owner (company admin id). All role queries are scoped by this column. */
    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Column(nullable = false)
    private String status = "Active"; // Active / Inactive

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}