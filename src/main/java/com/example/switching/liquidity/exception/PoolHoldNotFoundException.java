package com.example.switching.liquidity.exception;

public class PoolHoldNotFoundException extends RuntimeException {

    private final String txnId;

    public PoolHoldNotFoundException(String txnId) {
        super("Pool hold not found for transaction " + txnId);
        this.txnId = txnId;
    }

    public String getTxnId() {
        return txnId;
    }
}
