package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A learner's attempt at a Question Bank (the "Attempt Quiz" activity of a course module).
 * One row per learner per bank: UNIQUE(user_id, bank_id). A "Retest" re-uses the row (attempt_number goes up,
 * answers are cleared), so the Score page always shows the latest attempt. Plain ids (no FK), like the other ledger tables.
 */
@Entity
@Table(
        name = "quiz_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_quiz_attempt_user_bank", columnNames = {"user_id", "bank_id"}),
        indexes = @Index(name = "idx_quiz_attempt_course", columnList = "course_id")
)
@Getter
@Setter
@NoArgsConstructor
public class QuizAttemptEntity {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SUBMITTED = "SUBMITTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** The course (organize) section the bank lives in. */
    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "bank_id", nullable = false)
    private Long bankId;

    /** 1, 2, 3... how many times the learner has started this quiz (Score page: "Attempts - 01/03"). */
    @Column(name = "attempt_number", nullable = false, columnDefinition = "integer default 1")
    private Integer attemptNumber = 1;

    /** IN_PROGRESS or SUBMITTED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Filled when the attempt is finished. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    /** Comma separated ids of the question sections the learner has already submitted ("Submit Section"). */
    @Column(name = "submitted_sections", length = 1000)
    private String submittedSections;
}