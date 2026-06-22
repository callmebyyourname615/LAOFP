package com.example.switching.rtp.exception;

public class RtpAccessDeniedException extends RuntimeException {
    public RtpAccessDeniedException() {
        super("Caller is not permitted to access this RTP request");
    }
}
