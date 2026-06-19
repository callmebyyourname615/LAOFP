package com.example.switching.settlement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementCycleResponse(
        Long id,
        String cycleRef,
        LocalDate settlementDate,
        int cycleNumber,
        String status,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        LocalDateTime settledAt,
        int itemCount,
        List<SettlementPositionResponse> positions
) {}
