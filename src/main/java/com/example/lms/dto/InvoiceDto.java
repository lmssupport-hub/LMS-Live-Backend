package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Purchase history of the logged-in learner (GET /api/my-purchases), each with its invoice when one was issued. */
public class InvoiceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseItem {
        private Long purchaseId;
        private Long courseId;
        private String courseName;
        private LocalDateTime purchasedAt;
        /** Total paid. */
        private BigDecimal total;
        /** null for purchases made before tax existed. */
        private BigDecimal subtotal;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private boolean pricesIncludeTax;
        /** null = no invoice was issued for this purchase (built-in invoicing was off). */
        private Long invoiceId;
        private String invoiceNumber;
    }

    /** A rendered invoice ready to send. */
    public record PdfFile(String fileName, byte[] content) {
    }
}