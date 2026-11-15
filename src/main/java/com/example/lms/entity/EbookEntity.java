package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * An Ebook (Add Activity -> Ebook) of a course section.
 * The pages the instructor designed in the Ebook editor (sections with text / image / video blocks)
 * are stored as one JSON document in {@code content}; the Angular ebook model is the source of truth for its shape.
 */
@Entity
@Table(
        name = "ebooks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ebook_name_per_section", columnNames = {"section_id", "name"})
        },
        indexes = {
                @Index(name = "idx_ebook_section_id", columnList = "section_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EbookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a course section also removes its ebooks at DB level.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ebook_section"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SectionEntity section;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * JSON array of the ebook's sections. Plain TEXT via LONGVARCHAR (not @Lob) to avoid Postgres Large Object
     * auto-commit errors - same approach as PackageEntity.permissionsJson.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

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