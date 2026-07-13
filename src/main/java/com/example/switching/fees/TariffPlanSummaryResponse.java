package com.example.switching.fees;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TariffPlanSummaryResponse(
        UUID id,
        String name,
        int version,
        String status,
        OffsetDateTime effectiveFrom,
        int rules
) {}
