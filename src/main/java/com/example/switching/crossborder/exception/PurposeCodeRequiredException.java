package com.example.switching.crossborder.exception;

import java.math.BigDecimal;

public class PurposeCodeRequiredException extends RuntimeException {
    public PurposeCodeRequiredException(BigDecimal amount, long thresholdLak) {
        super("purposeCode and sourceOfFunds are required for transfers > LAK "
                + thresholdLak + " (requested: " + amount.toPlainString() + ")");
    }
}
