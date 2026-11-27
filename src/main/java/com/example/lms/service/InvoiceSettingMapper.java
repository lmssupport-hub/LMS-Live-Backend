package com.example.lms.service;

import com.example.lms.dto.InvoiceSettingDto;
import com.example.lms.entity.InvoiceSettingEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** Explicit, field-by-field mapping between the API shape and the table (no reflection / implicit mapping). */
public final class InvoiceSettingMapper {

    private InvoiceSettingMapper() {
    }

    /** Copies every field of the request onto the entity (new or existing). Texts are already validated. */
    public static void apply(InvoiceSettingDto.SaveRequest request, InvoiceSettingEntity entity) {
        entity.setBuiltInEnabled(request.isBuiltInEnabled());
        entity.setInvoicePrefix(trim(request.getInvoicePrefix()));
        entity.setNextInvoiceNumber(orOne(request.getNextInvoiceNumber()));
        entity.setCreditNotePrefix(trim(request.getCreditNotePrefix()));
        entity.setNextCreditNoteNumber(orOne(request.getNextCreditNoteNumber()));
        entity.setVatNumber(trim(request.getVatNumber()));
        entity.setTaxRate(request.getTaxRate() == null ? BigDecimal.ZERO : request.getTaxRate().setScale(2, RoundingMode.HALF_UP));
        entity.setPricesIncludeTax(request.isPricesIncludeTax());
        entity.setInvoiceForFullDiscount(request.isInvoiceForFullDiscount());

        entity.setShowLogo(request.isShowLogo());
        // a hidden logo is dropped, so an old picture never comes back by accident
        boolean keepLogo = request.isShowLogo() && request.getLogo() != null && !request.getLogo().isBlank();
        entity.setLogo(keepLogo ? request.getLogo() : null);
        entity.setLogoName(keepLogo ? trim(request.getLogoName()) : null);

        entity.setThankYouMessage(trim(request.getThankYouMessage()));
        entity.setTaxMessage(trim(request.getTaxMessage()));

        entity.getCustomTexts().clear();
        if (request.getTexts() != null) {
            request.getTexts().forEach((key, value) -> {
                String text = trim(value);
                if (!text.isEmpty()) entity.getCustomTexts().put(key, text);
            });
        }
    }

    public static InvoiceSettingDto.Response toResponse(InvoiceSettingEntity e) {
        Map<String, String> texts = new HashMap<>(e.getCustomTexts());
        return InvoiceSettingDto.Response.builder()
                .builtInEnabled(e.isBuiltInEnabled())
                .invoicePrefix(e.getInvoicePrefix())
                .nextInvoiceNumber(e.getNextInvoiceNumber())
                .creditNotePrefix(e.getCreditNotePrefix())
                .nextCreditNoteNumber(e.getNextCreditNoteNumber())
                .vatNumber(e.getVatNumber())
                .taxRate(e.getTaxRate())
                .pricesIncludeTax(e.isPricesIncludeTax())
                .invoiceForFullDiscount(e.isInvoiceForFullDiscount())
                .showLogo(e.isShowLogo())
                .logo(e.getLogo())
                .logoName(e.getLogoName())
                .thankYouMessage(e.getThankYouMessage())
                .taxMessage(e.getTaxMessage())
                .texts(texts)
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static long orOne(Long value) {
        return value == null ? 1L : value;
    }
}