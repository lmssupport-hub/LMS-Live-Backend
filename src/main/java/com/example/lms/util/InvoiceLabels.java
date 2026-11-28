package com.example.lms.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The invoice labels the admin may rename (Invoice Setup > Appearance) and their default text.
 * Keys and defaults are the same as INVOICE_TEXT_GROUPS in the Angular invoice-setup.model.ts.
 */
public final class InvoiceLabels {

    public static final Map<String, String> DEFAULTS = defaults();

    private InvoiceLabels() {
    }

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("invoiceNumber", "Invoice Number");
        m.put("invoice", "Invoice");
        m.put("invoiceDate", "Invoice Date");
        m.put("customerDetails", "Customer Details");
        m.put("description", "Description");
        m.put("unitPrice", "Unit Price");
        m.put("quantity", "Quantity");
        m.put("subtotal", "Subtotal");
        m.put("price", "Price");
        m.put("discount", "Discount");
        m.put("totalAmount", "Total Amount");
        m.put("website", "Website");
        m.put("email", "Email");
        m.put("pricesIncludeTax", "Prices Include Tax");
        m.put("tax", "Tax");
        m.put("taxIncluded", "Tax Included");
        m.put("refundReason", "Refund Reason");
        m.put("refundMethod", "Refund Method");
        m.put("invoiceReference", "Invoice Reference");
        m.put("refundedAmount", "Refunded Amount");
        m.put("paid", "Paid");
        m.put("refunded", "Refunded");
        m.put("pending", "Pending");
        m.put("completed", "Completed");
        return java.util.Collections.unmodifiableMap(m);
    }
}