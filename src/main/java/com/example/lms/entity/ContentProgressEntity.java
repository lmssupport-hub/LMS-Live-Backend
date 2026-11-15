package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * How far a learner has got in ONE content item of a course section (a multimedia file now, an e-book later)
 * and whether the section's quiz was submitted. One row per learner per item; the percent only ever goes up.
 * 100 = the item is COMPLETED. Plain ids (no FK), like the other ledger tables.
 */
@Entity
@Table(
        name = "content_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_content_progress_user_item", columnNames = {"user_id", "item_type", "item_id"}),
        indexes = @Index(name = "idx_content_progress_user_course", columnList = "user_id, course_id")
)
@Getter
@Setter
@NoArgsConstructor
public class ContentProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** MULTIMEDIA | QUIZ (| EBOOK later). */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    /** Multimedia id, or the question bank id for QUIZ. */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}