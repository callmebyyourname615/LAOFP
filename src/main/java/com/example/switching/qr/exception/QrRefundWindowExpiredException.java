package com.example.switching.qr.exception;

public class QrRefundWindowExpiredException extends RuntimeException {
    public QrRefundWindowExpiredException(String originalTxnId) {
        super("QR refund window has expired (max 30 days) for transaction: " + originalTxnId);
    }
}
