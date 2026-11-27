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
 * Invoice setup of ONE organization (company admin): Profile > Settings > Invoice Setup.
 * One row per organization: UNIQUE(organization_id), where organization_id is the company admin's user id
 * (PackageService.resolveOwnerId) - same rule as CertificateTemplateEntity.
 *
 * "Set Up" tab  : built-in switch, prefixes, next numbers, VAT number, 100% discount switch.
 * "Appearance"  : logo, thank-you / tax messages and the label overrides (customTexts).
 *
 * The logo is stored as a data URL (TEXT), the way the Angular page produces it.
 */
@Entity
@Table(
        name = "invoice_settings",
        uniqueConstraints = @UniqueConstraint(name = "uq_invoice_settings_org", columnNames = "organization_id")
)
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The company admin's user id. */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // ---------- Set Up ----------

    @Column(name = "built_in_enabled", nullable = false)
    private boolean builtInEnabled;

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "";

    @Column(name = "next_invoice_number", nullable = false)
    private long nextInvoiceNumber = 1;

    @Column(name = "credit_note_prefix", nullable = false, length = 20)
    private String creditNotePrefix = "";

    @Column(name = "next_credit_note_number", nullable = false)
    private long nextCreditNoteNumber = 1;

    @Column(name = "vat_number", nullable = false, length = 20)
    private String vatNumber = "";

    /** Tax in percent (0 - 100) added to / contained in every course price of this organization. 0 = no tax. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2, columnDefinition = "numeric(5,2) default 0")
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** true = the course price already contains the tax; false = tax is added on top of the price. */
    @Column(name = "prices_include_tax", nullable = false, columnDefinition = "boolean default false")
    private boolean pricesIncludeTax;

    /** true = also create an invoice when the purchase is 100% discounted. */
    @Column(name = "invoice_for_full_discount", nullable = false)
    private boolean invoiceForFullDiscount;

    // ---------- Appearance ----------

    @Column(name = "show_logo", nullable = false)
    private boolean showLogo;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @Column(name = "logo_name", length = 255)
    private String logoName;

    @Column(name = "thank_you_message", nullable = false, length = 500)
    private String thankYouMessage = "";

    @Column(name = "tax_message", nullable = false, length = 500)
    private String taxMessage = "";

    /** Label overrides, e.g. "invoice" -> "Tax Invoice". Only keys the admin changed are stored. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_setting_texts", joinColumns = @JoinColumn(name = "invoice_setting_id"))
    @MapKeyColumn(name = "text_key", length = 50)
    @Column(name = "text_value", nullable = false, length = 100)
    private Map<String, String> customTexts = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** User id of the admin who saved it last. */
    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}