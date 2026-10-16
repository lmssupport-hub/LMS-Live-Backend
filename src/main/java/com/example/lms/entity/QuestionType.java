package com.example.lms.entity;

import java.util.Locale;
import java.util.Optional;

/** Question types supported by M5F1 (Single Answer, Multiple Answer, Short Text). */
public enum QuestionType {
    SINGLE_ANSWER,
    MULTIPLE_ANSWER,
    SHORT_TEXT;

    /** Lenient, null-safe parser used for API / CSV input. Never throws. */
    public static Optional<QuestionType> from(String raw) {
        if (raw == null) return Optional.empty();
        String n = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (n) {
            case "SINGLE":
            case "SINGLE_ANSWER":
                return Optional.of(SINGLE_ANSWER);
            case "MULTIPLE":
            case "MULTIPLE_ANSWER":
                return Optional.of(MULTIPLE_ANSWER);
            case "SHORT":
            case "TEXT":
            case "SHORT_TEXT":
            case "SHORT_ANSWER":
                return Optional.of(SHORT_TEXT);
            default:
                return Optional.empty();
        }
    }
}