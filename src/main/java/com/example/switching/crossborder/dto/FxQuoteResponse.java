package com.example.switching.crossborder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxQuoteResponse(
        Long          quoteId,
        String        sourceCurrency,
        String        destCurrency,
        String        targetNetwork,
        BigDecimal    sourceAmount,
        BigDecimal    destAmount,
        BigDecimal    rate,
        BigDecimal    fee,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {}
