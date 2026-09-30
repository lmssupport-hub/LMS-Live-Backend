package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "multimedia_resources",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_multimedia_name_per_section", columnNames = {"section_id", "name"})
        },
        indexes = {
                @Index(name = "idx_multimedia_section_id", columnList = "section_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MultimediaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a section also removes its resource rows at DB level (files in Cloudinary are not touched by this).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_multimedia_section"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SectionEntity section;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    // VIDEO / AUDIO / PDF / DOCUMENT / PRESENTATION / SPREADSHEET / IMAGE / ARCHIVE / OTHER
    @Column(name = "resource_type", nullable = false, length = 30)
    private String resourceType;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    // Needed to delete / replace the file in Cloudinary later.
    @Column(name = "storage_public_id", nullable = false, length = 500)
    private String storagePublicId;

    // Cloudinary resource_type: image / video / raw
    @Column(name = "storage_resource_type", nullable = false, length = 20)
    private String storageResourceType;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

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