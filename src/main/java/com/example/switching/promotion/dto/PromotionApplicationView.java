package com.example.switching.promotion.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record PromotionApplicationView(UUID applicationId,UUID promotionId,String promotionCode,
    BigDecimal grossFee,BigDecimal discountAmount,BigDecimal netFee,String currency,String status) {}
