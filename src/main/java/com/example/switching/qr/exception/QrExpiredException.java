package com.example.switching.qr.exception;

public class QrExpiredException extends RuntimeException {
    public QrExpiredException(String qrId) {
        super("QR code has expired: " + qrId);
    }
}
