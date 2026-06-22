package com.example.switching.rtp.exception;

import com.example.switching.rtp.enums.RtpStatus;

public class RtpInvalidTransitionException extends RuntimeException {
    public RtpInvalidTransitionException(RtpStatus from, RtpStatus to) {
        super("RTP transition is not allowed: " + from + " -> " + to);
    }
}
