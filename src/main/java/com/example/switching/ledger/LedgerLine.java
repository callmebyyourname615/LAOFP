package com.example.switching.ledger;

import java.math.BigDecimal;

public record LedgerLine(String accountCode, Side side, BigDecimal amount, String narrative) {
    public enum Side { DEBIT, CREDIT }

    public LedgerLine {
        if (accountCode == null || accountCode.isBlank()) throw new IllegalArgumentException("accountCode is required");
        if (side == null) throw new IllegalArgumentException("side is required");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        amount = amount.stripTrailingZeros();
    }
}
