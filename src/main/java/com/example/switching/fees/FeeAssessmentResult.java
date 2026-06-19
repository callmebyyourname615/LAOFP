package com.example.switching.fees;
import java.math.BigDecimal;
import java.util.UUID;
public record FeeAssessmentResult(UUID tariffVersionId, UUID tariffRuleId, BigDecimal fee, String currency) {}
