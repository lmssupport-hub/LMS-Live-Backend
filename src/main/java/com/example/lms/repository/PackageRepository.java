package com.example.lms.repository;

import com.example.lms.entity.PackageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<PackageEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Signup-la auto assign aaga vendiya Free + Active package. */
    Optional<PackageEntity> findFirstByAvailablePackageAndAutoAssignOnSignupTrueAndStatusIgnoreCaseOrderByIdAsc(
            String availablePackage, String status);

    /** Vera oru Active package already auto-assign-la irukka nu check. */
    boolean existsByAutoAssignOnSignupTrueAndStatusIgnoreCaseAndIdNot(String status, Long id);

    /**
     * Search by Package Name + Status filter. status/search null = "All" / no search text.
     * CAST(... AS string): null bind parameter Postgres-la bytea nu resolve aagama irukka.
     */
    @Query("""
            SELECT p FROM PackageEntity p
            WHERE (:status IS NULL OR p.status = CAST(:status AS string))
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<PackageEntity> search(@Param("status") String status,
                               @Param("search") String search,
                               Pageable pageable);
}