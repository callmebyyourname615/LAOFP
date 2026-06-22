package com.example.switching.rtp.exception;

import java.util.UUID;

public class RtpNotFoundException extends RuntimeException {
    public RtpNotFoundException(UUID id) {
        super("RTP request not found: " + id);
    }
}
