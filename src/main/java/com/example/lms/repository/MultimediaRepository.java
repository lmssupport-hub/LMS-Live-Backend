package com.example.lms.repository;

import com.example.lms.entity.MultimediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MultimediaRepository extends JpaRepository<MultimediaEntity, Long> {

    List<MultimediaEntity> findBySectionIdOrderByCreatedAtAsc(Long sectionId);

    Optional<MultimediaEntity> findByIdAndSectionId(Long id, Long sectionId);

    boolean existsBySectionIdAndNameIgnoreCase(Long sectionId, String name);

    boolean existsBySectionIdAndNameIgnoreCaseAndIdNot(Long sectionId, String name, Long id);
}