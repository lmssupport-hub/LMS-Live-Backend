package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Permanent revenue ledger: one row every time a package is assigned to an admin.
 *
 * Deliberately NOT a foreign key to packages/users and it stores a snapshot of the
 * package name + price, so the history survives expiry, unassign, package edit and
 * package delete.
 */
@Entity
@Table(
        name = "package_purchases",
        indexes = {
                @Index(name = "idx_package_purchases_purchased_at", columnList = "purchased_at"),
                @Index(name = "idx_package_purchases_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PackagePurchaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Plain number (no FK) so deleting a package never deletes revenue history. */
    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    /** Price at the moment of purchase (snapshot). */
    @Column(nullable = false)
    private Double price;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;
}