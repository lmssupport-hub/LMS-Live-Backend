package com.example.lms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** All Question Bank entity classes in one file (static nested classes). */
public class QuestionBankEntities {

    private QuestionBankEntities() { }

    /**
     * The "Question Bank" activity that is added INSIDE an organize section.
     * Course -> Section (organize section) -> QuestionBank -> QuestionSection -> Question
     */
    @Entity(name = "QuestionBankEntity")
    @Table(
        name = "question_banks",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_bank_name_per_section", columnNames = {"section_id", "name"})
        },
        indexes = {
            @Index(name = "idx_bank_section_id", columnList = "section_id")
        }
    )
    @Getter @Setter @NoArgsConstructor
    public static class QuestionBankEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // DB-level cascade: deleting an organize section / course keeps working without knowing about banks.
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bank_section"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        private SectionEntity section;

        @Column(nullable = false, length = 100)
        private String name;

        @Column(name = "bank_order", nullable = false)
        private int bankOrder;

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

    /**
     * "Question Section" INSIDE a Question Bank (e.g. Section 1 = 10 questions, Section 2 = 5 questions).
     * This is NOT the course "organize section" (SectionEntity).
     *
     * NEW: score + feedback = the "Section points" panel of the design.
     */
    @Entity(name = "QuestionSectionEntity")
    @Table(
        name = "question_sections",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_qsection_name_per_bank", columnNames = {"question_bank_id", "name"})
        },
        indexes = {
            @Index(name = "idx_qsection_bank_order", columnList = "question_bank_id, section_order")
        }
    )
    @Getter @Setter @NoArgsConstructor
    public static class QuestionSectionEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "question_bank_id", nullable = false, foreignKey = @ForeignKey(name = "fk_qsection_bank"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        private QuestionBankEntity questionBank;

        @Column(nullable = false, length = 100)
        private String name;

        @Column(name = "section_order", nullable = false)
        private int sectionOrder;

        /** NEW: Section points -> Score (0..100). null = not set. */
        @Column(name = "score")
        private Integer score;

        /** NEW: Section points -> Feedback (max 500 chars). null = not set. */
        @Column(name = "feedback", length = 500)
        private String feedback;

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

    @Entity(name = "QuestionEntity")
    @Table(
        name = "questions",
        uniqueConstraints = {
            // NEW: one Idempotency-Key can create at most one question per question section.
            // NULL keys (import, copy, duplicate ...) never collide with each other.
            @UniqueConstraint(name = "uq_question_idempotency", columnNames = {"question_section_id", "idempotency_key"})
        },
        indexes = {
            @Index(name = "idx_question_qsection_order", columnList = "question_section_id, question_order")
        }
    )
    @Getter @Setter @NoArgsConstructor
    public static class QuestionEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // A question belongs to a QUESTION SECTION (inside a Question Bank),
        // not directly to the course "organize section".
        // ON DELETE CASCADE at DB level: deleting a question section removes its questions.
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "question_section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_question_qsection"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        private QuestionSectionEntity questionSection;

        @Column(name = "question_text", nullable = false, length = 500)
        private String questionText;

        @Enumerated(EnumType.STRING)
        @Column(name = "question_type", nullable = false, length = 30)
        private QuestionType questionType;

        @Enumerated(EnumType.STRING)
        @Column(name = "scoring_type", nullable = false, length = 20)
        private ScoringType scoringType = ScoringType.BASIC;

        @Column(name = "correct_score", nullable = false)
        private int correctScore = 0;

        @Column(name = "incorrect_score", nullable = false)
        private int incorrectScore = 0;

        @Column(name = "correct_feedback", length = 500)
        private String correctFeedback;

        @Column(name = "incorrect_feedback", length = 500)
        private String incorrectFeedback;

        /** Model answer for SHORT_TEXT questions only. */
        @Column(name = "expected_answer", length = 500)
        private String expectedAnswer;

        @Column(name = "hidden", nullable = false)
        private boolean hidden = false;

        @Column(name = "question_order", nullable = false)
        private int questionOrder;

        /**
         * NEW: value of the "Idempotency-Key" header of the POST that created this question
         * (the Angular app sends question.clientId). A retried POST returns this question
         * instead of creating a second one. Never changes after creation.
         */
        @Column(name = "idempotency_key", length = 64, updatable = false)
        private String idempotencyKey;

        /** Optimistic locking: concurrent edits of the same question (SRS edge case). */
        @Version
        private Long version;

        @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("optionOrder ASC")
        @BatchSize(size = 50)
        private List<QuestionOptionEntity> options = new ArrayList<>();

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

        public void replaceOptions(List<QuestionOptionEntity> newOptions) {
            this.options.clear();
            this.options.addAll(newOptions);
        }
    }

    @Entity(name = "QuestionOptionEntity")
    @Table(
        name = "question_options",
        indexes = {
            @Index(name = "idx_option_question_id", columnList = "question_id")
        }
    )
    @Getter @Setter @NoArgsConstructor
    public static class QuestionOptionEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "question_id", nullable = false, foreignKey = @ForeignKey(name = "fk_option_question"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        private QuestionEntity question;

        @Column(name = "option_text", nullable = false, length = 200)
        private String optionText;

        @Column(name = "is_correct", nullable = false)
        private boolean correct;

        /** Used only when the parent question uses ADVANCED scoring. */
        @Column(name = "score", nullable = false)
        private int score = 0;

        @Column(name = "option_order", nullable = false)
        private int optionOrder;
    }
}