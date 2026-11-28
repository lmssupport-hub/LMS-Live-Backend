package com.example.lms.util;

/** Invoice number = prefix + the counter padded to 5 digits (INV-00001). The Angular preview shows the same. */
public final class InvoiceNumbers {

    private InvoiceNumbers() {
    }

    public static String format(String prefix, long number) {
        return (prefix == null ? "" : prefix) + String.format("%05d", number);
    }
}