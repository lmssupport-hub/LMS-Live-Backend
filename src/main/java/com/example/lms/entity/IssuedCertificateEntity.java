package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A certificate a learner has EARNED by completing a course (shown on "My Certificate").
 * One per learner per course: UNIQUE(user_id, course_id).
 *
 * The learner / course / organization names are copied here when the certificate is issued, so the
 * certificate keeps reading the same even if the course is renamed or deleted later. The design
 * (colours, text, logo...) is the organization's current certificate template.
 * Plain ids (no FK), like the other ledger tables.
 */
@Entity
@Table(
        name = "issued_certificates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_issued_certificate_user_course", columnNames = {"user_id", "course_id"}),
                // numbering is per organization: 1, 2, 3... inside each organization
                @UniqueConstraint(name = "uq_issued_certificate_org_seq", columnNames = {"organization_id", "sequence_no"}),
                @UniqueConstraint(name = "uq_issued_certificate_number", columnNames = "certificate_number")
        },
        indexes = @Index(name = "idx_issued_certificate_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class IssuedCertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** Company admin id that owns the template (PackageService.resolveOwnerId). */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "learner_name", nullable = false, length = 200)
    private String learnerName;

    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    /** 1, 2, 3... counted inside the organization (every organization starts at 1). Null only on rows from before numbering. */
    @Column(name = "sequence_no")
    private Integer sequenceNo;

    /** e.g. CERT-12-2026-000001 = organization 12, completed in 2026, its 1st certificate. */
    @Column(name = "certificate_number", nullable = false, length = 40)
    private String certificateNumber;

    /** The day the learner completed the course. */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}