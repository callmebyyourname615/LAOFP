package com.example.switching.crossborder.exception;

public class CorridorNotAvailableException extends RuntimeException {
    public CorridorNotAvailableException(String detail) {
        super("FX corridor is not available: " + detail);
    }
}
