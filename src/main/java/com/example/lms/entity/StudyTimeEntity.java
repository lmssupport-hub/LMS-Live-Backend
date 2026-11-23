package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Seconds a learner has actively spent studying ONE course (course content page open and visible).
 * One row per learner per course. The seconds are added by the SERVER from the time between two
 * pings (see StudyTimeService), never taken from the browser, so they cannot be inflated.
 * Plain ids (no FK), like the other ledger tables.
 */
@Entity
@Table(
        name = "study_time",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_time_user_course", columnNames = {"user_id", "course_id"}),
        indexes = @Index(name = "idx_study_time_course", columnList = "course_id")
)
@Getter
@Setter
@NoArgsConstructor
public class StudyTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "seconds", nullable = false)
    private long seconds = 0L;

    @Column(name = "last_ping_at", nullable = false)
    private LocalDateTime lastPingAt;

    public StudyTimeEntity(Long userId, Long courseId, LocalDateTime now) {
        this.userId = userId;
        this.courseId = courseId;
        this.lastPingAt = now;
    }
}