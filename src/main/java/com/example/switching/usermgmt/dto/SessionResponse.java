package com.example.switching.usermgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID sessionFamilyId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt) {}
