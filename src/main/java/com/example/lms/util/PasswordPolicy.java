package com.example.lms.util;

import java.util.regex.Pattern;

public final class PasswordPolicy {
    public static final int MIN = 8;
    public static final int MAX = 16;
    public static final String STRONG_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$";
    private static final Pattern STRONG = Pattern.compile(STRONG_REGEX);

    private PasswordPolicy() {}

    public static boolean isStrong(String p) {
        return p != null && STRONG.matcher(p).matches();
    }

    public static boolean hasEmoji(String p) {
        return p != null && p.codePoints().anyMatch(cp ->
                cp > 0xFFFF || Character.getType(cp) == Character.OTHER_SYMBOL);
    }
}