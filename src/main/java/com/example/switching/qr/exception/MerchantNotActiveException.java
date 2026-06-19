package com.example.switching.qr.exception;

public class MerchantNotActiveException extends RuntimeException {
    public MerchantNotActiveException(String merchantId) {
        super("Merchant is not active: " + merchantId);
    }
}
