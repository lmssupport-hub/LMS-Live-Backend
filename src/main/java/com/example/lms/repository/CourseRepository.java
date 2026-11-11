package com.example.lms.repository;

import com.example.lms.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every finder is scoped by organizationId (Multi-Tenant Data Isolation). */
public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    Optional<CourseEntity> findByIdAndOrganizationId(Long id, Long organizationId);

    List<CourseEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<CourseEntity> findByOrganizationIdAndStatusIgnoreCaseOrderByCreatedAtDesc(Long organizationId, String status);

    boolean existsByNameIgnoreCaseAndOrganizationId(String name, Long organizationId);

    boolean existsByNameIgnoreCaseAndOrganizationIdAndIdNot(String name, Long organizationId, Long id);
}
