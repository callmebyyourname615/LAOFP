package com.example.switching.qr.exception;

public class QrAlreadyUsedException extends RuntimeException {
    public QrAlreadyUsedException(String qrId) {
        super("QR code has already been used: " + qrId);
    }
}
