package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.role = 'ADMIN' " +
           "AND u.companyAdminId IS NULL AND u.deletedAt IS NULL " +
           "AND (:active IS NULL OR u.active = :active) " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAdmins(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);

    // Super Admin > Admins: a company's own ADMIN (never an invited team member, never a deleted one).
    Page<User> findByRoleAndCompanyAdminIdIsNullAndDeletedAtIsNull(String role, Pageable pageable);

    Optional<User> findByIdAndRoleAndCompanyAdminIdIsNullAndDeletedAtIsNull(Long id, String role);

    // Super Admin > Admins > Export as CSV
    List<User> findByRoleAndCompanyAdminIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdAsc(String role);

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
    @Query("SELECT u FROM User u WHERE u.roleId = :roleId AND u.companyAdminId = :companyAdminId AND u.deletedAt IS NULL")
    List<User> findByRoleIdAndCompanyAdminId(@Param("roleId") Long roleId, @Param("companyAdminId") Long companyAdminId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.roleId = :roleId AND u.companyAdminId = :companyAdminId AND u.deletedAt IS NULL")
    long countByRoleIdAndCompanyAdminId(@Param("roleId") Long roleId, @Param("companyAdminId") Long companyAdminId);

    /**
     * Heartbeat: refreshes last_seen_at of a user who is online. One bulk UPDATE (no entity load); it never
     * flips online back to true, so a heartbeat that is still in flight cannot undo a logout.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :now WHERE u.id = :id AND u.online = true")
    int touchLastSeen(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Previous heartbeat time of a user who is online (empty = offline / never seen). */
    @Query("SELECT u.lastSeenAt FROM User u WHERE u.id = :id AND u.online = true")
    java.util.Optional<LocalDateTime> findLastSeenAtIfOnline(@Param("id") Long id);

    /** Atomic add to the user's total time on the platform. */
    @Modifying
    @Query("UPDATE User u SET u.platformSeconds = u.platformSeconds + :seconds WHERE u.id = :id")
    int addPlatformSeconds(@Param("id") Long id, @Param("seconds") long seconds);

    /** One query for the whole role list: [roleId, count]. */
    @Query("SELECT u.roleId, COUNT(u) FROM User u " +
           "WHERE u.companyAdminId = :ownerId AND u.roleId IS NOT NULL AND u.deletedAt IS NULL GROUP BY u.roleId")
    List<Object[]> countUsersByRole(@Param("ownerId") Long ownerId);

    // ------------------------------------------------------------------
    // User Management (All Users page) - always scoped to ONE company.
    // status: null = all, otherwise "Active" / "Inactive" / "Suspended"
    // (Suspended = active account whose suspension window covers :today).
    // ------------------------------------------------------------------
    @Query("SELECT u FROM User u WHERE u.companyAdminId = :ownerId AND u.deletedAt IS NULL " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.role) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.department) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR :status = '' " +
           "     OR (:status = 'Inactive' AND u.active = false) " +
           "     OR (:status = 'Suspended' AND u.active = true " +
           "         AND u.suspendedFrom <= :today AND u.suspendedTo >= :today) " +
           "     OR (:status = 'Active' AND u.active = true " +
           "         AND NOT (u.suspendedFrom IS NOT NULL AND u.suspendedTo IS NOT NULL " +
           "                  AND u.suspendedFrom <= :today AND u.suspendedTo >= :today)))")
    Page<User> searchManagedUsers(@Param("ownerId") Long ownerId,
                                  @Param("search") String search,
                                  @Param("status") String status,
                                  @Param("today") LocalDate today,
                                  Pageable pageable);

    /** One user of ONE company (404 for another company's user -> no id probing). */
    Optional<User> findByIdAndCompanyAdminIdAndDeletedAtIsNull(Long id, Long companyAdminId);

    List<User> findByIdInAndCompanyAdminIdAndDeletedAtIsNull(java.util.Collection<Long> ids, Long companyAdminId);
}