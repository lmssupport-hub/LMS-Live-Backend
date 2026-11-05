package com.example.lms.repository;

import com.example.lms.entity.PackagePurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PackagePurchaseRepository extends JpaRepository<PackagePurchaseEntity, Long> {

    List<PackagePurchaseEntity> findAllByOrderByPurchasedAtAsc();

    /** from inclusive, to exclusive. */
    List<PackagePurchaseEntity> findByPurchasedAtGreaterThanEqualAndPurchasedAtLessThanOrderByPurchasedAtAsc(
            LocalDateTime from, LocalDateTime to);

    /** Used by the one-time backfill so it never inserts the same purchase twice. */
    boolean existsByUserIdAndPurchasedAt(Long userId, LocalDateTime purchasedAt);
}