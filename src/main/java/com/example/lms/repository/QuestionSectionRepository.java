package com.example.lms.repository;

import com.example.lms.entity.QuestionBankEntities.QuestionSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionSectionRepository extends JpaRepository<QuestionSectionEntity, Long> {

    List<QuestionSectionEntity> findByQuestionBankIdOrderBySectionOrderAscIdAsc(Long bankId);

    Optional<QuestionSectionEntity> findByIdAndQuestionBankId(Long id, Long bankId);

    boolean existsByQuestionBankIdAndNameIgnoreCase(Long bankId, String name);

    boolean existsByQuestionBankIdAndNameIgnoreCaseAndIdNot(Long bankId, String name, Long id);

    long countByQuestionBankId(Long bankId);

    @Query("SELECT COALESCE(MAX(s.sectionOrder), 0) FROM QuestionSectionEntity s WHERE s.questionBank.id = :bankId")
    Integer findMaxOrderByBankId(@Param("bankId") Long bankId);
}
