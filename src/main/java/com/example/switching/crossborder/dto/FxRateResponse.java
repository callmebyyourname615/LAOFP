package com.example.switching.crossborder.dto;

import java.math.BigDecimal;

public record FxRateResponse(
        Long       corridorId,
        String     sourceCurrency,
        String     destCurrency,
        String     targetNetwork,
        BigDecimal indicativeRate,
        BigDecimal feePercent,
        BigDecimal feeFixed,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        int        validForSeconds   // quote TTL hint
) {}
