package com.example.lms.repository;

import com.example.lms.entity.EbookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EbookRepository extends JpaRepository<EbookEntity, Long> {

    /** Light projection: lists never load the (large) content column. */
    interface EbookSummary {
        Long getId();
        String getName();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }

    List<EbookSummary> findSummariesBySectionIdOrderByCreatedAtAsc(Long sectionId);

    Optional<EbookEntity> findByIdAndSectionId(Long id, Long sectionId);

    boolean existsByIdAndSectionId(Long id, Long sectionId);

    boolean existsBySectionIdAndNameIgnoreCase(Long sectionId, String name);

    boolean existsBySectionIdAndNameIgnoreCaseAndIdNot(Long sectionId, String name, Long id);
}