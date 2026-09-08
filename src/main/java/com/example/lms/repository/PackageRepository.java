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

    /**
     * Doc06 Edge Case #6: "prevent duplicate package names when creating OR
     * updating a package". A single indexed query is cheaper and race-safer
     * than fetch-then-filter-in-memory.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Backs Doc06 Field #1 (Search by Package Name) and Field #2 (Filter by
     * Status: All / Active / Inactive). `status` and `search` are nullable
     * to represent "All Packages" / "no search text". Paginated for
     * performance as the catalog grows (production-grade requirement).
     *
     * IMPORTANT — explicit CAST(:search AS string):
     * When `:search` is null (the common case — an empty search box),
     * PostgreSQL's JDBC driver sends that bind parameter with no type
     * information. Postgres only defaults an *untyped literal* to `text`;
     * an untyped *bind parameter* used inside a function/operator call
     * (here, the CONCAT('%', :search, '%') that LOWER() wraps) can resolve
     * ambiguously — in practice this was resolving to `bytea`, producing
     * "function lower(bytea) does not exist" the moment the search box was
     * empty. Casting the parameter to `string` forces Postgres to see it as
     * text before the concat/LOWER ever runs, independent of whether the
     * value is null or a real search term. Same treatment applied to
     * `:status` defensively, since it hits the same class of ambiguity.
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