package com.example.lms.repository;

import com.example.lms.entity.SystemFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemFeatureRepository extends JpaRepository<SystemFeatureEntity, String> {

    List<SystemFeatureEntity> findByActiveTrueOrderByCategoryIdAsc();

    /** Used by the permission screen so categories/features follow display_order, not alphabetical category_id. */
    List<SystemFeatureEntity> findByActiveTrueOrderByDisplayOrderAscIdAsc();
}