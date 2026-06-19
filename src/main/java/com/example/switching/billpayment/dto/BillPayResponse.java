package com.example.switching.billpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillPayResponse(
        Long          paymentId,
        String        txnRef,
        String        billerCode,
        String        billRef,
        BigDecimal    amount,
        String        receiptNumber,
        String        status,
        LocalDateTime confirmedAt
) {}
