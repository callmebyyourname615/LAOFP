package com.example.switching.usermgmt.dto;

import java.util.Set;

public record AuthResponse(
        boolean mfaRequired,
        String mfaToken,
        String accessToken,
        String refreshToken,
        long expiresIn,
        Set<String> roles,
        Set<String> permissions) {
    public static AuthResponse mfa(String token, long expiresIn) {
        return new AuthResponse(true, token, null, null, expiresIn, Set.of(), Set.of());
    }
}
