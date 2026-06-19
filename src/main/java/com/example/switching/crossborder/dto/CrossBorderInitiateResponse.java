package com.example.switching.crossborder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CrossBorderInitiateResponse(
        Long          cbId,
        String        txnRef,
        String        targetNetwork,
        String        networkTxnId,
        String        sourceCurrency,
        BigDecimal    sourceAmount,
        String        destCurrency,
        BigDecimal    destAmount,
        BigDecimal    fee,
        String        status,
        LocalDateTime completedAt
) {}
