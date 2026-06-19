package com.example.switching.aml.sanctions.model;

import java.time.Instant;
import java.util.List;

/** Immutable provider response after a complete payload has been fetched and parsed. */
public record SanctionsSnapshot(
        String providerCode,
        String sourceReference,
        Instant fetchedAt,
        String contentSha256,
        List<SanctionsEntry> entries) {

    public SanctionsSnapshot {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("providerCode must not be blank");
        }
        providerCode = providerCode.trim().toUpperCase();
        sourceReference = sourceReference == null ? "" : sourceReference.trim();
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        if (contentSha256 == null || !contentSha256.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("contentSha256 must be a 64-character SHA-256 hex value");
        }
        contentSha256 = contentSha256.toLowerCase();
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
