package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Latest billing details a company admin entered on the Payment page.
 * One row per user (upserted on every purchase). No card data is ever stored here.
 */
@Entity
@Table(
        name = "billing_details",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_details_user", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class BillingDetailsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "billing_name", nullable = false, length = 200)
    private String billingName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 100)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}