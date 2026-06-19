package com.example.switching.billpayment.exception;

public class BillTokenExpiredException extends RuntimeException {
    public BillTokenExpiredException(Long tokenId) {
        super("Bill token has expired or already been used: " + tokenId);
    }
}
