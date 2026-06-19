package com.example.switching.qr.exception;

public class QrChecksumException extends RuntimeException {
    public QrChecksumException() {
        super("QR code CRC-16/CCITT checksum verification failed");
    }
}
