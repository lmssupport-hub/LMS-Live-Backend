package com.example.lms.repository;

import com.example.lms.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

    /** An invoice is only visible to the learner who paid for it. */
    Optional<InvoiceEntity> findByIdAndUserId(Long id, Long userId);

    List<InvoiceEntity> findByPurchaseIdIn(Collection<Long> purchaseIds);
}