package com.example.switching.aml.sanctions;

import java.util.UUID;

public record SanctionsImportResult(
        UUID runId,
        String providerCode,
        int parsed,
        int inserted,
        int updated,
        int deactivated) {
}
