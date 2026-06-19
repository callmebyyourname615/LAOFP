package com.example.switching.liquidity.exception;

import java.math.BigDecimal;

public class InsufficientPoolBalanceException extends RuntimeException {

    private final String pspId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;

    public InsufficientPoolBalanceException(String pspId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("Insufficient pool balance for PSP " + pspId + ": requested " + requestedAmount
                + ", available " + availableBalance);
        this.pspId = pspId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public String getPspId() {
        return pspId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
