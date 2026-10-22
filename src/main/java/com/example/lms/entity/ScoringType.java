package com.example.lms.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * BASIC    = points for a correct / incorrect answer (question level).
 * ADVANCED = points per choice (option level).
 */
public enum ScoringType {
    BASIC,
    ADVANCED;

    public static Optional<ScoringType> from(String raw) {
        if (raw == null) return Optional.empty();
        String n = raw.trim().toUpperCase(Locale.ROOT);
        for (ScoringType t : values()) {
            if (t.name().equals(n)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
