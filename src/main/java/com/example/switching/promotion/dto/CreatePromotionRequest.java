package com.example.switching.promotion.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.example.switching.promotion.enums.PromotionType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record CreatePromotionRequest(
    @NotBlank @Size(max=64) String code,
    @NotBlank @Size(max=160) String name,
    @NotNull PromotionType type,
    @Min(0) int priority,
    boolean combinable,
    @NotBlank String funderParticipantId,
    @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
    @NotNull @DecimalMin("0.0000") BigDecimal budgetCap,
    @NotNull @DecimalMin("0.0000") BigDecimal discountValue,
    @NotBlank @Pattern(regexp="FIXED|PERCENT") String discountMode,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotEmpty List<JsonNode> eligibilityRules) {}
