package com.example.switching.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyPrecisionPolicyTest {
    @Test
    void roundsWithHalfEvenAtFourDecimals() {
        assertThat(MoneyPrecisionPolicy.normalize(new BigDecimal("1.23445")))
                .isEqualByComparingTo("1.2344");
        assertThat(MoneyPrecisionPolicy.normalize(new BigDecimal("1.23455")))
                .isEqualByComparingTo("1.2346");
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> MoneyPrecisionPolicy.normalize(
                new BigDecimal("123456789012345678901.0000")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsNegativeWhereNonNegativeRequired() {
        assertThatThrownBy(() -> MoneyPrecisionPolicy.requireNonNegative(new BigDecimal("-0.0001")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
