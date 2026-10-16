package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "questions",
    indexes = {
        @Index(name = "idx_question_section_order", columnList = "section_id, question_order")
    }
)
@Getter @Setter @NoArgsConstructor
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ON DELETE CASCADE at DB level: SectionService.deleteSection() keeps working
    // without having to know about questions.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_question_section"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SectionEntity section;

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