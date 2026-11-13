package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row = one learner's progress in one section (module) of a course.
 * No row = NOT_STARTED. UNIQUE(user_id, section_id) keeps it to one row per learner per section.
 * Plain ids (no FK) like the other ledger tables; rows of a deleted section are simply ignored.
 */
@Entity
@Table(
        name = "section_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_section_progress_user_section", columnNames = {"user_id", "section_id"}),
        indexes = @Index(name = "idx_section_progress_user_course", columnList = "user_id, course_id")
)
@Getter
@Setter
@NoArgsConstructor
public class SectionProgressEntity {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** IN_PROGRESS or COMPLETED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 0-100: share of the module's content the learner has gone through. 100 = COMPLETED. Only ever goes up. */
    @Column(name = "progress_percent", nullable = false, columnDefinition = "integer default 0")
    private Integer progressPercent = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}