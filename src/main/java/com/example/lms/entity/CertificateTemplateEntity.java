package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * The certificate design of ONE organization (company admin). One row per organization:
 * UNIQUE(organization_id), where organization_id is the company admin's user id
 * (PackageService.resolveOwnerId) - so the admin and every instructor / learner of that company see the same design.
 *
 * The text fields hold {{tags}} such as {{learner_name}}; they are replaced with real values when a
 * certificate is generated for a learner (a later feature). Images are stored as data URLs (TEXT), the way
 * the designer produces them. Plain TEXT via LONGVARCHAR (not @Lob) - same approach as EbookEntity / PackageEntity.
 */
@Entity
@Table(
        name = "certificate_templates",
        uniqueConstraints = @UniqueConstraint(name = "uq_certificate_template_org", columnNames = "organization_id")
)
@Getter
@Setter
@NoArgsConstructor
public class CertificateTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The company admin's user id. */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "subtitle", nullable = false, length = 200)
    private String subtitle;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "footer_note", nullable = false, length = 300)
    private String footerNote;

    @Column(name = "date_label", nullable = false, length = 100)
    private String dateLabel;

    @Column(name = "date_text", nullable = false, length = 200)
    private String dateText;

    @Column(name = "signatory_name", nullable = false, length = 150)
    private String signatoryName;

    @Column(name = "signatory_title", nullable = false, length = 200)
    private String signatoryTitle;

    /** Hex colour such as #2f7684. */
    @Column(name = "accent", nullable = false, length = 7)
    private String accent;

    @Column(name = "background", nullable = false, length = 7)
    private String background;

    /** classic | elegant | modern */
    @Column(name = "font", nullable = false, length = 20)
    private String font;

    /** double | single | none */
    @Column(name = "border_style", nullable = false, length = 20)
    private String border;

    /** left | center | right */
    @Column(name = "logo_position", nullable = false, length = 20)
    private String logoPosition;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "background_image", columnDefinition = "TEXT")
    private String backgroundImage;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** User id of the admin who saved it last. */
    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}