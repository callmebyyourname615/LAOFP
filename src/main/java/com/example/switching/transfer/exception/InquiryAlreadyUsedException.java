package com.example.switching.transfer.exception;

public class InquiryAlreadyUsedException extends RuntimeException {

    public InquiryAlreadyUsedException(String message) {
        super(message);
    }
}