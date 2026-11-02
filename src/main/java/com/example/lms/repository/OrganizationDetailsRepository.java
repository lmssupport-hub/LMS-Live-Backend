package com.example.lms.repository;

import com.example.lms.entity.OrganizationDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationDetailsRepository extends JpaRepository<OrganizationDetails, Long> {
    Optional<OrganizationDetails> findByUserId(Long userId);
}