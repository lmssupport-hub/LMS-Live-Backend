package com.example.lms.repository;

import com.example.lms.entity.SectionProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionProgressRepository extends JpaRepository<SectionProgressEntity, Long> {

    List<SectionProgressEntity> findByUserIdAndCourseId(Long userId, Long courseId);

    Optional<SectionProgressEntity> findByUserIdAndSectionId(Long userId, Long sectionId);
}