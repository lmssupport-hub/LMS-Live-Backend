package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "packages",
        indexes = {
                @Index(name = "idx_packages_name", columnList = "name"),
                @Index(name = "idx_packages_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PackageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "available_package", nullable = false)
    private String availablePackage; // Trial / Basic / Standard / Premium / Existing

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle; // Monthly / Yearly / Custom (Trial only)

    /**
     * Validity in days: 30 = Monthly, 365 = Yearly, user-entered for Trial.
     * Nullable at DB level so old rows survive; the service falls back to
     * 30/365 from billingCycle when it is null.
     */
    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "user_limit", nullable = false)
    private Integer userLimit;

    @Column(name = "storage_limit")
    private Integer storageLimit;

    /** Trial checkbox: "Auto-assign on signup". The default keeps existing rows valid on ddl-auto update. */
    @Column(name = "auto_assign_on_signup", nullable = false, columnDefinition = "boolean not null default false")
    private boolean autoAssignOnSignup = false;

    /** Non-Trial checkbox: "Show on website". The default keeps existing rows valid on ddl-auto update. */
    @Column(name = "show_on_website", nullable = false, columnDefinition = "boolean not null default false")
    private boolean showOnWebsite = false;

    @Column(nullable = false)
    private String status = "Active"; // Active / Inactive

    /**
     * Plain TEXT via LONGVARCHAR (not @Lob) to avoid Postgres Large Object
     * auto-commit errors.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "permissions_json", columnDefinition = "TEXT")
    private String permissionsJson; // stores Category>Feature>Permission tree as JSON

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

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