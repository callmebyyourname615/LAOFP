package com.example.switching.crossborder.exception;

public class FxQuoteExpiredException extends RuntimeException {
    public FxQuoteExpiredException(Long quoteId) {
        super("FX quote has expired or already been used: " + quoteId);
    }
}
