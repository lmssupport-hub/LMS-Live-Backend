package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.role = 'ADMIN' " +
           "AND (:active IS NULL OR u.active = :active) " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAdmins(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    // FIXED - role is stored as "Instructor" in DB, not "INSTRUCTOR", so a plain
    // equality match was returning an empty list. IgnoreCase fixes that.
    List<User> findByRoleIgnoreCaseAndActiveTrue(String role);
}