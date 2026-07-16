package com.example.switching.usermgmt.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SmosTokenClaims(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        String tokenId,
        Long participantId,
        UUID sessionFamilyId,
        Instant issuedAt,
        Instant expiresAt) {}
