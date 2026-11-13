package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ledger of PAID course enrollments (learner pays -> gets enrolled).
 *
 * Separate from package_purchases (company admin buys a package), so the two payments
 * never mix. A snapshot of the course name + price is stored, with no foreign keys,
 * so the history survives a course edit / delete / unenroll.
 * No card data is ever stored here.
 */
@Entity
@Table(
        name = "course_purchases",
        indexes = {
                @Index(name = "idx_course_purchases_user_course", columnList = "user_id, course_id"),
                @Index(name = "idx_course_purchases_org", columnList = "organization_id"),
                @Index(name = "idx_course_purchases_purchased_at", columnList = "purchased_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CoursePurchaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Plain numbers (no FK) so deleting a course never deletes payment history. */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** Company (tenant) that owns the course. */
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    /** Price (USD) at the moment of purchase - always read from the DB, never from the browser. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // ---- billing snapshot (display only) ----
    @Column(name = "billing_name", nullable = false, length = 200)
    private String billingName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "billing_email", nullable = false, length = 100)
    private String billingEmail;

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

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;
}