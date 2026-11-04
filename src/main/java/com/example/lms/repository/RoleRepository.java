package com.example.lms.repository;

import com.example.lms.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    // Roles are unique PER OWNER (company admin), not system-wide.
    boolean existsByCreatedByAdminIdAndNameIgnoreCase(Long ownerId, String name);

    boolean existsByCreatedByAdminIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);

    List<RoleEntity> findAllByCreatedByAdminId(Long ownerId);

    List<RoleEntity> findAllByCreatedByAdminIdAndStatus(Long ownerId, String status);

    Optional<RoleEntity> findByIdAndCreatedByAdminId(Long id, Long ownerId);
}