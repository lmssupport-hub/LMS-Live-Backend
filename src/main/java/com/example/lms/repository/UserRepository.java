package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // NEW - instructors of ONE organization (company admin id) for the course Instructor dropdown.
    List<User> findByRoleIgnoreCaseAndActiveTrueAndCompanyAdminId(String role, Long companyAdminId);

    // NEW - used by PackageExpiryScheduler: every admin whose package duration has ended.
    List<User> findByPackageIdIsNotNullAndPackageExpiresAtLessThanEqual(LocalDateTime now);

    // NEW - used by the Assigned admins list (remaining days).
    List<User> findByPackageId(Long packageId);

    // NEW - Role Management: users of ONE company who hold a given role.
    List<User> findByRoleIdAndCompanyAdminId(Long roleId, Long companyAdminId);

    long countByRoleIdAndCompanyAdminId(Long roleId, Long companyAdminId);

    /** One query for the whole role list: [roleId, count]. */
    @Query("SELECT u.roleId, COUNT(u) FROM User u " +
           "WHERE u.companyAdminId = :ownerId AND u.roleId IS NOT NULL GROUP BY u.roleId")
    List<Object[]> countUsersByRole(@Param("ownerId") Long ownerId);
}