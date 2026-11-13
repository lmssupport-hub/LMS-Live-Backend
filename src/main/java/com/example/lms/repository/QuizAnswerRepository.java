package com.example.lms.repository;

import com.example.lms.entity.QuizAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswerEntity, Long> {

    List<QuizAnswerEntity> findByAttemptId(Long attemptId);

    Optional<QuizAnswerEntity> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}