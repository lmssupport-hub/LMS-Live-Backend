package com.example.lms.repository;

import com.example.lms.entity.QuestionBankEntities.QuestionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {

    @EntityGraph(attributePaths = "options")
    List<QuestionEntity> findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(Long questionSectionId);

    @EntityGraph(attributePaths = "options")
    Optional<QuestionEntity> findByIdAndQuestionSectionId(Long id, Long questionSectionId);

    /** Bank-scoped lookup so a caller can never pull questions from another Question Bank. */
    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM QuestionEntity q WHERE q.id IN :ids AND q.questionSection.questionBank.id = :bankId")
    List<QuestionEntity> findByIdsInBank(@Param("ids") Collection<Long> ids, @Param("bankId") Long bankId);

    long countByQuestionSectionId(Long questionSectionId);

    @Query("SELECT COALESCE(MAX(q.questionOrder), 0) FROM QuestionEntity q WHERE q.questionSection.id = :qsId")
    Integer findMaxOrderByQuestionSectionId(@Param("qsId") Long questionSectionId);

    /** "Existing Questions": questions in the same Question Bank but in OTHER question sections. Keyword must be pre-lowered and wildcard-free. */
    @Query(value = "SELECT q FROM QuestionEntity q "
            + "WHERE q.questionSection.questionBank.id = :bankId AND q.questionSection.id <> :excludeQuestionSectionId "
            + "AND LOWER(q.questionText) LIKE CONCAT('%', :keyword, '%') "
            + "ORDER BY q.id DESC",
            countQuery = "SELECT COUNT(q) FROM QuestionEntity q "
            + "WHERE q.questionSection.questionBank.id = :bankId AND q.questionSection.id <> :excludeQuestionSectionId "
            + "AND LOWER(q.questionText) LIKE CONCAT('%', :keyword, '%')")
    Page<QuestionEntity> searchExisting(@Param("bankId") Long bankId,
                                        @Param("excludeQuestionSectionId") Long excludeQuestionSectionId,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);
}
