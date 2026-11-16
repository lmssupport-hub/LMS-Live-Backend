package com.example.lms.repository;

import com.example.lms.entity.CertificateTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplateEntity, Long> {

    Optional<CertificateTemplateEntity> findByOrganizationId(Long organizationId);
}