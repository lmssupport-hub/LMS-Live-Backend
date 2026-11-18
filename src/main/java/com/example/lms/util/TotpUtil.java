package com.example.lms.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s) - the profile every authenticator app uses by default
 * (Google Authenticator, Microsoft Authenticator, Authy ...). No third-party library needed.
 */
public final class TotpUtil {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Accept the previous and the next 30 s step too, to tolerate clock drift on the phone. */
    private static final int WINDOW = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** 160-bit random secret, Base32 (what the QR code / "enter key manually" carries). */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static long currentStep() {
        return Instant.now().getEpochSecond() / PERIOD_SECONDS;
    }

    /**
     * Returns the time step the code belongs to, or -1 when it is wrong. Steps {@code <= lastUsedStep}
     * are rejected so the same code can never be used twice (replay protection); pass -1 for "none yet".
     */
    public static long matchStep(String secretBase32, String code, long lastUsedStep) {
        if (secretBase32 == null || code == null || !code.matches("^\\d{" + DIGITS + "}$")) {
            return -1;
        }
        byte[] key = base32Decode(secretBase32);
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        long now = currentStep();
        long match = -1;
        // No early exit: every window step is always checked, so timing does not reveal which one matched.
        for (long step = now - WINDOW; step <= now + WINDOW; step++) {
            byte[] expected = hotp(key, step).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, given) && step > lastUsedStep) {
                match = step;
            }
        }
        return match;
    }

    /** otpauth:// URI that Google Authenticator reads from the QR code. */
    public static String otpauthUri(String issuer, String account, String secretBase32) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(account)
                + "?secret=" + secretBase32
                + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    static String hotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 not available", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase();
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
