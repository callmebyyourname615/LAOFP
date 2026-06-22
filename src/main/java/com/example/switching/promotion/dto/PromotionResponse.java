package com.example.switching.promotion.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.example.switching.promotion.enums.PromotionStatus;
import com.example.switching.promotion.enums.PromotionType;
public record PromotionResponse(UUID id,String code,String name,PromotionType type,PromotionStatus status,int priority,
    boolean combinable,String funderParticipantId,String currency,BigDecimal budgetCap,BigDecimal budgetReserved,
    BigDecimal budgetConsumed,BigDecimal budgetRemaining,BigDecimal discountValue,String discountMode,
    Instant startsAt,Instant endsAt) {}
