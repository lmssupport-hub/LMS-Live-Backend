package com.example.lms.repository;

import com.example.lms.entity.InvoiceSettingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceSettingRepository extends JpaRepository<InvoiceSettingEntity, Long> {

    Optional<InvoiceSettingEntity> findByOrganizationId(Long organizationId);

    /**
     * Same row, but locked until the transaction ends. Used when an invoice number is taken, so two purchases
     * at the same moment can never get the same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSettingEntity s where s.organizationId = :organizationId")
    Optional<InvoiceSettingEntity> findForUpdateByOrganizationId(@Param("organizationId") Long organizationId);
}