package com.example.switching.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class PromotionEligibilityEvaluatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PromotionEligibilityEvaluator evaluator = new PromotionEligibilityEvaluator();
    private final PromotionContext context = new PromotionContext(
            "TX-PROMO-001", "BANK_A", "QR", "PACS008", "LAK",
            new BigDecimal("150000"), new BigDecimal("5000"), "GOLD",
            Instant.parse("2026-06-22T00:00:00Z"), Map.of());

    @Test
    void matchesWhitelistedStringAndNumericConditions() throws Exception {
        var rule = mapper.readTree("""
                {"all":[
                  {"field":"channel","operator":"EQ","value":"QR"},
                  {"field":"customerSegment","operator":"IN","value":["GOLD","PLATINUM"]},
                  {"field":"amount","operator":"GTE","value":100000},
                  {"field":"grossFee","operator":"BETWEEN","value":[1000,6000]}
                ]}
                """);

        assertThat(evaluator.matches(rule, context)).isTrue();
    }

    @Test
    void returnsFalseWhenOneRequiredConditionDoesNotMatch() throws Exception {
        var rule = mapper.readTree("""
                {"all":[
                  {"field":"channel","operator":"EQ","value":"CARD"},
                  {"field":"amount","operator":"LTE","value":200000}
                ]}
                """);

        assertThat(evaluator.matches(rule, context)).isFalse();
    }

    @Test
    void returnsFalseWhenAnAllowedContextFieldIsMissing() throws Exception {
        var rule = mapper.readTree("""
                {"all":[{"field":"customerSegment","operator":"EQ","value":"GOLD"}]}
                """);
        PromotionContext noSegment = new PromotionContext(
                context.transactionReference(), context.participantId(), context.channel(),
                context.messageType(), context.currency(), context.amount(), context.grossFee(),
                null, context.occurredAt(), context.attributes());

        assertThat(evaluator.matches(rule, noSegment)).isFalse();
    }

    @Test
    void rejectsNonWhitelistedFieldsAndExecutableExpressions() throws Exception {
        var unsafe = mapper.readTree("""
                {"all":[{"field":"T(java.lang.Runtime).getRuntime()","operator":"EQ","value":"x"}]}
                """);

        assertThatThrownBy(() -> evaluator.matches(unsafe, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsMalformedNumericAndUnboundedRules() throws Exception {
        var stringNumber = mapper.readTree("""
                {"all":[{"field":"amount","operator":"GTE","value":"100000"}]}
                """);
        var reversedRange = mapper.readTree("""
                {"all":[{"field":"amount","operator":"BETWEEN","value":[200000,100000]}]}
                """);
        StringBuilder conditions = new StringBuilder("{\"all\":[");
        for (int index = 0; index < 33; index++) {
            if (index > 0) conditions.append(',');
            conditions.append("{\"field\":\"channel\",\"operator\":\"EQ\",\"value\":\"QR\"}");
        }
        conditions.append("]}");
        var tooMany = mapper.readTree(conditions.toString());

        assertThatThrownBy(() -> evaluator.matches(stringNumber, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
        assertThatThrownBy(() -> evaluator.matches(reversedRange, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum");
        assertThatThrownBy(() -> evaluator.matches(tooMany, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condition limit");
    }
}
