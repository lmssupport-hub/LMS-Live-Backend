package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row = one learner enrolled in one course.
 *
 * The UNIQUE(course_id, user_id) constraint is the final guard against duplicate
 * enrollments (SRS Edge Cases #1 and #7): even if two requests race past the
 * service-level pre-check, the database rejects the second one.
 */
@Entity
@Table(
        name = "course_enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_course_user", columnNames = {"course_id", "user_id"}),
        indexes = @Index(name = "idx_enrollment_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private LocalDateTime enrolledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Null = enrollment never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 0-100. Enforced again by a DB CHECK constraint. */
    @Column(name = "progress_percent", nullable = false)
    @Builder.Default
    private Integer progressPercent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.NOT_STARTED;

    /** 0-100, null until the learner has a score. */
    @Column(name = "score_percent", precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.enrolledAt == null) {
            this.enrolledAt = LocalDateTime.now();
        }
    }
}