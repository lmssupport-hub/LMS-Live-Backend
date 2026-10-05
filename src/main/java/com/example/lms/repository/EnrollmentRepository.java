package com.example.lms.repository;

import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    // ---------- projections (read only what the UI needs - no password hashes, no entity graph) ----------

    interface EnrolledLearnerRow {
        Long getEnrollmentId();
        Long getUserId();
        String getFirstName();
        String getLastName();
        LocalDateTime getEnrolledAt();
        LocalDateTime getCompletedAt();
        LocalDateTime getExpiresAt();
        Integer getProgressPercent();
        EnrollmentStatus getStatus();
        BigDecimal getScorePercent();
    }

    interface EligibleUserRow {
        Long getId();
        String getFirstName();
        String getLastName();
        String getEmail();
    }

    // ---------- Enrolled Course List ----------

    /**
     * Single joined query (no N+1). Ordering is fixed in the query so pagination is stable;
     * callers must pass an UNSORTED Pageable.
     */
    @Query(value = """
            select e.id as enrollmentId,
                   e.userId as userId,
                   u.firstName as firstName,
                   u.lastName as lastName,
                   e.enrolledAt as enrolledAt,
                   e.completedAt as completedAt,
                   e.expiresAt as expiresAt,
                   e.progressPercent as progressPercent,
                   e.status as status,
                   e.scorePercent as scorePercent
            from EnrollmentEntity e
            join User u on u.id = e.userId
            where e.courseId = :courseId
            order by u.firstName asc, u.lastName asc, e.id asc
            """,
            countQuery = """
            select count(e) from EnrollmentEntity e
            join User u on u.id = e.userId
            where e.courseId = :courseId
            """)
    Page<EnrolledLearnerRow> findEnrolledLearners(@Param("courseId") Long courseId, Pageable pageable);

    // ---------- Enroll User Form ----------

    /**
     * Active learners NOT already enrolled in the course, optionally filtered by name/email.
     * {@code search} must be non-null (pass "" for none) and pre-escaped with '!' as the escape char.
     */
    @Query("""
            select u.id as id, u.firstName as firstName, u.lastName as lastName, u.email as email
            from User u
            where upper(u.role) = 'LEARNER'
              and u.active = true
              and not exists (select 1 from EnrollmentEntity e
                              where e.courseId = :courseId and e.userId = u.id)
              and (:search = ''
                   or lower(u.firstName) like lower(concat('%', :search, '%')) escape '!'
                   or lower(u.lastName)  like lower(concat('%', :search, '%')) escape '!'
                   or lower(u.email)     like lower(concat('%', :search, '%')) escape '!')
            order by u.firstName asc, u.lastName asc, u.id asc
            """)
    List<EligibleUserRow> findEligibleLearners(@Param("courseId") Long courseId,
                                               @Param("search") String search,
                                               Pageable pageable);

    /** How many of the given ids are active learners. Used to reject forged / stale ids server-side. */
    @Query("""
            select count(u) from User u
            where u.id in :userIds
              and u.active = true
              and upper(u.role) = 'LEARNER'
            """)
    long countEligibleLearners(@Param("userIds") Collection<Long> userIds);

    @Query("select e.userId from EnrollmentEntity e where e.courseId = :courseId and e.userId in :userIds")
    List<Long> findEnrolledUserIds(@Param("courseId") Long courseId, @Param("userIds") Collection<Long> userIds);

    // ---------- Unenroll ----------

    /** Bulk delete in one statement; returns the number of rows actually removed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EnrollmentEntity e where e.courseId = :courseId and e.userId in :userIds")
    int deleteByCourseIdAndUserIdIn(@Param("courseId") Long courseId, @Param("userIds") Collection<Long> userIds);
}