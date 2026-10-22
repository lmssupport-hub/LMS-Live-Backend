package com.example.lms.repository;

import com.example.lms.entity.QuestionBankEntities.QuestionBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBankEntity, Long> {

    List<QuestionBankEntity> findBySectionIdOrderByBankOrderAscIdAsc(Long sectionId);

    /** Scoped lookup: the bank must belong to this organize section AND this course. */
    Optional<QuestionBankEntity> findByIdAndSectionIdAndSectionCourseId(Long id, Long sectionId, Long courseId);

    boolean existsBySectionIdAndNameIgnoreCase(Long sectionId, String name);

    boolean existsBySectionIdAndNameIgnoreCaseAndIdNot(Long sectionId, String name, Long id);

    @Query("SELECT COALESCE(MAX(b.bankOrder), 0) FROM QuestionBankEntity b WHERE b.section.id = :sectionId")
    Integer findMaxOrderBySectionId(@Param("sectionId") Long sectionId);
}
