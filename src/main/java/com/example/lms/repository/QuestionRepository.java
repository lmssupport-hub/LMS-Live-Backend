package com.example.lms.repository;

import com.example.lms.entity.QuestionEntity;
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
    List<QuestionEntity> findBySectionIdOrderByQuestionOrderAscIdAsc(Long sectionId);

    @EntityGraph(attributePaths = "options")
    Optional<QuestionEntity> findByIdAndSectionId(Long id, Long sectionId);

    /** Course-scoped lookup so a caller can never pull questions from another course. */
    @EntityGraph(attributePaths = "options")
    List<QuestionEntity> findByIdInAndSectionCourseId(Collection<Long> ids, Long courseId);

    long countBySectionId(Long sectionId);

    @Query("SELECT COALESCE(MAX(q.questionOrder), 0) FROM QuestionEntity q WHERE q.section.id = :sectionId")
    Integer findMaxOrderBySectionId(@Param("sectionId") Long sectionId);

    /** "Existing Questions": questions in the same course but in OTHER sections. Keyword must be pre-lowered and wildcard-free. */
    @Query(value = "SELECT q FROM QuestionEntity q "
            + "WHERE q.section.course.id = :courseId AND q.section.id <> :excludeSectionId "
            + "AND LOWER(q.questionText) LIKE CONCAT('%', :keyword, '%') "
            + "ORDER BY q.id DESC",
            countQuery = "SELECT COUNT(q) FROM QuestionEntity q "
            + "WHERE q.section.course.id = :courseId AND q.section.id <> :excludeSectionId "
            + "AND LOWER(q.questionText) LIKE CONCAT('%', :keyword, '%')")
    Page<QuestionEntity> searchExisting(@Param("courseId") Long courseId,
                                        @Param("excludeSectionId") Long excludeSectionId,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);
}