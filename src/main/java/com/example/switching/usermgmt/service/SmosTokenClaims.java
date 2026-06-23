package com.example.switching.usermgmt.service;

import java.time.Instant;
import java.util.Set;

public record SmosTokenClaims(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        String tokenId,
        Long participantId,
        Instant issuedAt,
        Instant expiresAt) {}
