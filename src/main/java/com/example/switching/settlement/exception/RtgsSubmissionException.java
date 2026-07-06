package com.example.switching.settlement.exception;

public class RtgsSubmissionException extends RuntimeException {
    public RtgsSubmissionException(String message) {
        super(message);
    }

    public RtgsSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
