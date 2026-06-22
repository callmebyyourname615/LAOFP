package com.example.switching.promotion.service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
public record PromotionContext(String transactionReference,String participantId,String channel,String messageType,
        String currency,BigDecimal amount,BigDecimal grossFee,String customerSegment,Instant occurredAt,Map<String,String> attributes) {}
