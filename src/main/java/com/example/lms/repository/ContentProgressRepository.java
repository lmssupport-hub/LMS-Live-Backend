package com.example.lms.repository;

import com.example.lms.entity.ContentProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentProgressRepository extends JpaRepository<ContentProgressEntity, Long> {

    List<ContentProgressEntity> findByUserIdAndCourseId(Long userId, Long courseId);

    Optional<ContentProgressEntity> findByUserIdAndItemTypeAndItemId(Long userId, String itemType, Long itemId);
}