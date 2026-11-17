package com.example.lms.repository;

import com.example.lms.entity.IssuedCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import com.example.lms.entity.User;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuedCertificateRepository extends JpaRepository<IssuedCertificateEntity, Long> {

    List<IssuedCertificateEntity> findByUserIdOrderByIssuedAtDescIdDesc(Long userId);

    Optional<IssuedCertificateEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    /** Highest number used so far in the organization (0 when it has none yet). */
    @Query("select coalesce(max(c.sequenceNo), 0) from IssuedCertificateEntity c where c.organizationId = :organizationId")
    int findMaxSequenceNo(@Param("organizationId") Long organizationId);

    /**
     * Locks the organization's admin row until the transaction ends, so two learners finishing at the same moment
     * can't be given the same number (the second one waits, then sees the first one's number).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :organizationId")
    Optional<User> lockOrganization(@Param("organizationId") Long organizationId);

    /** Course ids the learner already has a certificate for. */
    @Query("select c.courseId from IssuedCertificateEntity c where c.userId = :userId")
    Set<Long> findCourseIdsByUserId(@Param("userId") Long userId);
}