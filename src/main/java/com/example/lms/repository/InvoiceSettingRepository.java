package com.example.lms.repository;

import com.example.lms.entity.InvoiceSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceSettingRepository extends JpaRepository<InvoiceSettingEntity, Long> {

    Optional<InvoiceSettingEntity> findByOrganizationId(Long organizationId);
}