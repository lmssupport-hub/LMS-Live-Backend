package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "courses",
       uniqueConstraints = @UniqueConstraint(name = "uk_course_org_name", columnNames = {"organization_id", "name"}),
       indexes = @Index(name = "idx_course_org", columnList = "organization_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CHANGED: unique per organization (see uk_course_org_name), no longer unique across the whole app.
    @Column(nullable = false, length = 255)
    private String name;

    // NEW: tenant owner = the company ADMIN's user id (PackageService.resolveOwnerId). Set on create, never changes.
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}