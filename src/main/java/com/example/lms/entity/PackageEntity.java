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
    private String availablePackage; // Basic / Standard / Premium / Existing

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle; // Monthly / Yearly

    @Column(name = "user_limit", nullable = false)
    private Integer userLimit;

    @Column(name = "storage_limit")
    private Integer storageLimit;

    @Column(nullable = false)
    private String status = "Active"; // Active / Inactive

    /**
     * FIXED: was previously annotated @Lob. On Postgres, @Lob on a String
     * field makes Hibernate treat this as a CLOB, which is read through
     * Postgres's Large Object API (lo_open/lo_read). That API requires an
     * active transaction with autocommit OFF — but plain read methods like
     * PackageService.getAllPackages() have no @Transactional, so they run
     * under Hikari's default autocommit=true. Every GET /api/packages call
     * was therefore guaranteed to fail with:
     *
     *   org.postgresql.util.PSQLException: Large Objects may not be used
     *   in auto-commit mode.
     *
     * The DB column is just `TEXT`, which Postgres treats identically to
     * VARCHAR — there is no reason to route it through the Large Object
     * subsystem at all. @JdbcTypeCode(SqlTypes.LONGVARCHAR) tells Hibernate
     * to read/write this column with plain setString()/getString() calls
     * instead, exactly like `description` above, avoiding LO entirely.
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