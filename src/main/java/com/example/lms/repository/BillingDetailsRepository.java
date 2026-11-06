package com.example.lms.repository;

import com.example.lms.entity.BillingDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingDetailsRepository extends JpaRepository<BillingDetailsEntity, Long> {

    Optional<BillingDetailsEntity> findByUserId(Long userId);
}