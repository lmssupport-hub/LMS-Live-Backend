package com.example.lms.service;

import com.example.lms.dto.InvoiceDto;
import com.example.lms.entity.CoursePurchaseEntity;
import com.example.lms.entity.InvoiceEntity;
import com.example.lms.entity.InvoiceSettingEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CoursePurchaseRepository;
import com.example.lms.repository.InvoiceRepository;
import com.example.lms.repository.InvoiceSettingRepository;
import com.example.lms.util.InvoiceNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Issues an invoice for a paid course purchase and serves the learner's purchase history + invoice PDFs.
 *
 * Issuing runs inside the purchase transaction (CoursePaymentService.purchase): the purchase, the enrollment,
 * the invoice and the next invoice number are saved together or not at all.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSettingRepository settingRepository;
    private final CoursePurchaseRepository purchaseRepository;
    private final CourseAccessService access;
    private final InvoicePdfRenderer pdfRenderer;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceSettingRepository settingRepository,
                          CoursePurchaseRepository purchaseRepository,
                          CourseAccessService access,
                          InvoicePdfRenderer pdfRenderer) {
        this.invoiceRepository = invoiceRepository;
        this.settingRepository = settingRepository;
        this.purchaseRepository = purchaseRepository;
        this.access = access;
        this.pdfRenderer = pdfRenderer;
    }

    /**
     * Creates the invoice of a saved purchase, or returns empty when the organization does not want one:
     *  - no Invoice Setup saved, or "Built-in invoicing" is not active
     *  - the purchase is free (total 0) and "Invoice for 100% discounts" is off
     */
    @Transactional
    public Optional<InvoiceEntity> issueFor(CoursePurchaseEntity purchase) {
        // locks the setup row until commit, so the number below cannot be handed out twice
        InvoiceSettingEntity setting = settingRepository
                .findForUpdateByOrganizationId(purchase.getOrganizationId()).orElse(null);
        if (setting == null || !setting.isBuiltInEnabled()) {
            return Optional.empty();
        }
        if (purchase.getAmount().signum() == 0 && !setting.isInvoiceForFullDiscount()) {
            return Optional.empty();
        }

        long number = setting.getNextInvoiceNumber();
        setting.setNextInvoiceNumber(number + 1);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setPurchaseId(purchase.getId());
        invoice.setOrganizationId(purchase.getOrganizationId());
        invoice.setUserId(purchase.getUserId());
        invoice.setInvoiceNumber(InvoiceNumbers.format(setting.getInvoicePrefix(), number));
        invoice.setIssuedAt(purchase.getPurchasedAt());

        invoice.setBillingName(purchase.getBillingName());
        invoice.setCompanyName(purchase.getCompanyName());
        invoice.setBillingEmail(purchase.getBillingEmail());
        invoice.setAddress(purchase.getAddress());
        invoice.setCity(purchase.getCity());
        invoice.setState(purchase.getState());
        invoice.setPostalCode(purchase.getPostalCode());
        invoice.setCountry(purchase.getCountry());

        invoice.setCourseName(purchase.getCourseName());
        BigDecimal total = purchase.getAmount();
        invoice.setSubtotal(purchase.getSubtotal() != null ? purchase.getSubtotal() : total);
        invoice.setTaxRate(purchase.getTaxRate() != null ? purchase.getTaxRate() : BigDecimal.ZERO);
        invoice.setTaxAmount(purchase.getTaxAmount() != null ? purchase.getTaxAmount() : BigDecimal.ZERO);
        invoice.setTotal(total);
        invoice.setPricesIncludeTax(purchase.isPricesIncludeTax());

        invoice.setVatNumber(setting.getVatNumber());
        invoice.setLogo(setting.isShowLogo() ? setting.getLogo() : null);
        invoice.setThankYouMessage(setting.getThankYouMessage());
        invoice.setTaxMessage(setting.getTaxMessage());
        invoice.setLabels(new HashMap<>(setting.getCustomTexts()));

        InvoiceEntity saved = invoiceRepository.save(invoice);
        log.info("Invoice issued: invoiceId={}, number={}, purchaseId={}, organizationId={}",
                saved.getId(), saved.getInvoiceNumber(), purchase.getId(), purchase.getOrganizationId());
        return Optional.of(saved);
    }

    /** The logged-in learner's paid courses, newest first, each with its invoice id / number when there is one. */
    @Transactional(readOnly = true)
    public List<InvoiceDto.PurchaseItem> myPurchases() {
        User user = access.currentUser();
        List<CoursePurchaseEntity> purchases = purchaseRepository.findByUserIdOrderByPurchasedAtDesc(user.getId());
        if (purchases.isEmpty()) {
            return List.of();
        }
        Map<Long, InvoiceEntity> invoiceByPurchase = invoiceRepository
                .findByPurchaseIdIn(purchases.stream().map(CoursePurchaseEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(InvoiceEntity::getPurchaseId, Function.identity()));

        return purchases.stream().map(p -> {
            InvoiceEntity invoice = invoiceByPurchase.get(p.getId());
            return InvoiceDto.PurchaseItem.builder()
                    .purchaseId(p.getId())
                    .courseId(p.getCourseId())
                    .courseName(p.getCourseName())
                    .purchasedAt(p.getPurchasedAt())
                    .total(p.getAmount())
                    .subtotal(p.getSubtotal())
                    .taxRate(p.getTaxRate())
                    .taxAmount(p.getTaxAmount())
                    .pricesIncludeTax(p.isPricesIncludeTax())
                    .invoiceId(invoice == null ? null : invoice.getId())
                    .invoiceNumber(invoice == null ? null : invoice.getInvoiceNumber())
                    .build();
        }).toList();
    }

    /** The invoice PDF. Only the learner who paid can get it; anyone else gets 404 (the id is not revealed). */
    @Transactional(readOnly = true)
    public InvoiceDto.PdfFile pdfFor(Long invoiceId) {
        User user = access.currentUser();
        InvoiceEntity invoice = invoiceRepository.findByIdAndUserId(invoiceId, user.getId())
                .orElseThrow(() -> new ApiException("Invoice not found.", HttpStatus.NOT_FOUND));
        byte[] content = pdfRenderer.render(invoice);
        return new InvoiceDto.PdfFile(fileName(invoice), content);
    }

    static String fileName(InvoiceEntity invoice) {
        return invoice.getInvoiceNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }
}