package com.example.lms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits a price into subtotal / tax / total. Pure maths, no Spring, no database.
 *
 *  - prices EXCLUDE tax: tax is added on top  (total = price + tax)
 *  - prices INCLUDE tax: the price already contains the tax (total = price, tax is taken out of it)
 *
 * Every amount has 2 decimals, rounded HALF_UP (the usual invoice rounding).
 */
public final class TaxCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private TaxCalculator() {
    }

    public record Breakdown(BigDecimal subtotal, BigDecimal taxRate, BigDecimal taxAmount,
                            BigDecimal total, boolean pricesIncludeTax) {
    }

    /** @param ratePercent tax rate in percent, 0 - 100 (null = no tax) */
    public static Breakdown calculate(BigDecimal price, BigDecimal ratePercent, boolean pricesIncludeTax) {
        BigDecimal p = (price == null ? BigDecimal.ZERO : price).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal rate = (ratePercent == null ? BigDecimal.ZERO : ratePercent).setScale(SCALE, RoundingMode.HALF_UP);
        if (rate.signum() < 0 || rate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100.");
        }
        BigDecimal zero = BigDecimal.ZERO.setScale(SCALE);
        if (rate.signum() == 0) {
            return new Breakdown(p, rate, zero, p, pricesIncludeTax);
        }
        if (pricesIncludeTax) {
            BigDecimal tax = p.multiply(rate).divide(HUNDRED.add(rate), SCALE, RoundingMode.HALF_UP);
            return new Breakdown(p.subtract(tax), rate, tax, p, true);
        }
        BigDecimal tax = p.multiply(rate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        return new Breakdown(p, rate, tax, p.add(tax), false);
    }
}