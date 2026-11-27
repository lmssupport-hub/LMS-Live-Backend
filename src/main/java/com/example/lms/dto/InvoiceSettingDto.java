package com.example.lms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * API shape of Profile > Settings > Invoice Setup. Field names match the Angular `InvoiceSetting` model.
 * Request level rules (size, format) live here; rules that need several fields or the database
 * (logo required when shown, unknown label keys, prefixes that clash) are checked in InvoiceSettingService.
 */
public class InvoiceSettingDto {

    private static final String PREFIX = "^[A-Za-z0-9_\\-/]{0,20}$";
    private static final String VAT = "^[A-Za-z0-9 \\-]{0,20}$";
    /** Logo arrives as a data URL from the page (JPG / PNG only), at most 2 MB before encoding. */
    private static final String LOGO_DATA_URL = "^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$";
    private static final long MAX_NUMBER = 9_999_999_999L;

    /** Body of PUT /api/invoice-settings. */
    @Data
    @NoArgsConstructor
    public static class SaveRequest {

        private boolean builtInEnabled;

        @Pattern(regexp = PREFIX, message = "Invoice prefix can have letters, digits, - _ / and at most 20 characters.")
        private String invoicePrefix;

        /** null / empty = start from 1. */
        @Min(value = 1, message = "Next invoice number must be 1 or more.")
        @Max(value = MAX_NUMBER, message = "Next invoice number can have at most 10 digits.")
        private Long nextInvoiceNumber;

        @Pattern(regexp = PREFIX, message = "Credit note prefix can have letters, digits, - _ / and at most 20 characters.")
        private String creditNotePrefix;

        @Min(value = 1, message = "Next credit note number must be 1 or more.")
        @Max(value = MAX_NUMBER, message = "Next credit note number can have at most 10 digits.")
        private Long nextCreditNoteNumber;

        @Pattern(regexp = VAT, message = "VAT number can have letters, digits, spaces and - (at most 20 characters).")
        private String vatNumber;

        /** null / empty = no tax. */
        @DecimalMin(value = "0", message = "Tax rate must be 0 or more.")
        @DecimalMax(value = "100", message = "Tax rate cannot be more than 100.")
        @Digits(integer = 3, fraction = 2, message = "Tax rate can have at most 2 decimals.")
        private BigDecimal taxRate;

        private boolean pricesIncludeTax;

        private boolean invoiceForFullDiscount;

        private boolean showLogo;

        @Size(max = 3_000_000, message = "The logo is too large. Use an image of 2 MB or less.")
        @Pattern(regexp = LOGO_DATA_URL, message = "The logo must be a JPG or PNG image.")
        private String logo;

        @Size(max = 255, message = "Logo file name is too long.")
        private String logoName;

        @Size(max = 500, message = "Thank you message must be at most 500 characters.")
        private String thankYouMessage;

        @Size(max = 500, message = "Tax message must be at most 500 characters.")
        private String taxMessage;

        /** Label overrides keyed by the keys of the Angular text groups (e.g. "invoice", "totalAmount"). */
        private Map<@Size(max = 50) String, @Size(max = 100, message = "Each text must be at most 100 characters.") String> texts = new HashMap<>();
    }

    /** The saved setup. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private boolean builtInEnabled;
        private String invoicePrefix;
        private long nextInvoiceNumber;
        private String creditNotePrefix;
        private long nextCreditNoteNumber;
        private String vatNumber;
        private BigDecimal taxRate;
        private boolean pricesIncludeTax;
        private boolean invoiceForFullDiscount;
        private boolean showLogo;
        private String logo;
        private String logoName;
        private String thankYouMessage;
        private String taxMessage;
        private Map<String, String> texts;
        private LocalDateTime updatedAt;
    }
}