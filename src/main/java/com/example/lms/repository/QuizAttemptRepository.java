package com.example.lms.repository;

import com.example.lms.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, Long> {

    Optional<QuizAttemptEntity> findByUserIdAndBankId(Long userId, Long bankId);
}