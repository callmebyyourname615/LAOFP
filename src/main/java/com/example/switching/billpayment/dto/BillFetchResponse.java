package com.example.switching.billpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillFetchResponse(
        Long          tokenId,
        Long          billerId,
        String        billerCode,
        String        billRef,
        BigDecimal    amount,
        LocalDate     dueDate,
        String        customerName,
        LocalDateTime validUntil   // expires_at — 10 min from fetch
) {}
