package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * One issued invoice of a PAID course purchase.
 *
 * Everything the PDF shows is a SNAPSHOT taken when the invoice was issued (customer, amounts, logo, messages,
 * label overrides), so changing Invoice Setup or editing / deleting the course later never changes an old invoice.
 * The PDF itself is not stored; it is drawn from this row each time it is downloaded.
 *
 * invoice_number is unique per organization (UNIQUE(organization_id, invoice_number)); purchase_id is unique,
 * so one purchase can never get two invoices.
 */
@Entity
@Table(
        name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_invoices_purchase", columnNames = "purchase_id"),
                @UniqueConstraint(name = "uq_invoices_org_number", columnNames = {"organization_id", "invoice_number"})
        },
        indexes = @Index(name = "idx_invoices_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class InvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    /** Company admin's user id (the seller). */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** The learner who paid (the buyer). Only this user can download the invoice. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    // ---- customer (billing details of the Payment page) ----
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

    // ---- what was bought ----
    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;
    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    // ---- look of the invoice (copied from Invoice Setup at issue time) ----
    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @Column(name = "thank_you_message", length = 500)
    private String thankYouMessage;

    @Column(name = "tax_message", length = 500)
    private String taxMessage;

    /** Label overrides in force when the invoice was issued (e.g. "invoice" -> "Tax Invoice"). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_labels", joinColumns = @JoinColumn(name = "invoice_id"))
    @MapKeyColumn(name = "label_key", length = 50)
    @Column(name = "label_value", nullable = false, length = 100)
    private Map<String, String> labels = new HashMap<>();
}