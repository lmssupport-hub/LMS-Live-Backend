package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "question_options",
    indexes = {
        @Index(name = "idx_option_question_id", columnList = "question_id")
    }
)
@Getter @Setter @NoArgsConstructor
public class QuestionOptionEntity {

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