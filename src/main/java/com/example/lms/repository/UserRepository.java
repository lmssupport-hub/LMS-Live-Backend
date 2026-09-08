package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    // Backs GET /api/auth/signup/admins. Returns User rows only —
    // packageId is carried on the row so the frontend can fetch package
    // details separately from PackageService, no join here.
    @Query("SELECT u FROM User u WHERE u.role = 'ADMIN' " +
           "AND (:active IS NULL OR u.active = :active) " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAdmins(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}