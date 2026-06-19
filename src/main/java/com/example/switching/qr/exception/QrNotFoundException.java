package com.example.switching.qr.exception;

public class QrNotFoundException extends RuntimeException {
    public QrNotFoundException(String qrId) {
        super("QR code not found: " + qrId);
    }
}
