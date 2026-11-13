package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One saved answer of an attempt. UNIQUE(attempt_id, question_id). No row = not answered. */
@Entity
@Table(
        name = "quiz_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_quiz_answer_attempt_question", columnNames = {"attempt_id", "question_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class QuizAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** Comma separated option ids (single / multiple answer questions). */
    @Column(name = "option_ids", length = 500)
    private String optionIds;

    /** Short text answer. */
    @Column(name = "text_answer", length = 500)
    private String textAnswer;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;
}