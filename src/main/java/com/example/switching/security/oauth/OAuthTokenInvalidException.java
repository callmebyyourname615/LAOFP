package com.example.switching.security.oauth;

public class OAuthTokenInvalidException extends RuntimeException {

    public OAuthTokenInvalidException(String message) {
        super(message);
    }
}
