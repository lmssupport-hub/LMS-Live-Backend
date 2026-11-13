package com.example.lms.repository;

import com.example.lms.entity.CoursePurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoursePurchaseRepository extends JpaRepository<CoursePurchaseEntity, Long> {

    /** Has this learner already paid for this course? (re-enroll after unenroll needs no second payment) */
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    List<CoursePurchaseEntity> findByUserIdOrderByPurchasedAtDesc(Long userId);

    List<CoursePurchaseEntity> findByOrganizationIdOrderByPurchasedAtDesc(Long organizationId);
}