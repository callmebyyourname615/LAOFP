package com.example.switching.financial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Canonical monetary precision policy shared by payment and ledger code. */
public final class MoneyPrecisionPolicy {
    public static final int PRECISION = 24;
    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private MoneyPrecisionPolicy() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        BigDecimal normalized = value.setScale(SCALE, ROUNDING_MODE);
        if (normalized.precision() > PRECISION) {
            throw new ArithmeticException("Money value exceeds NUMERIC(24,4)");
        }
        return normalized;
    }

    public static BigDecimal requireNonNegative(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("Money value must not be negative");
        }
        return normalized;
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Money value must be positive");
        }
        return normalized;
    }
}
