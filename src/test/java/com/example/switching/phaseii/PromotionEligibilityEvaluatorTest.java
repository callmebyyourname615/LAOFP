package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.switching.promotion.service.PromotionContext;
import com.example.switching.promotion.service.PromotionEligibilityEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;

class PromotionEligibilityEvaluatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PromotionEligibilityEvaluator evaluator = new PromotionEligibilityEvaluator();

    @Test
    void appliesWhitelistedDeterministicRule() throws Exception {
        var rule = mapper.readTree("""
            {"all":[
              {"field":"participantId","operator":"EQ","value":"BANK_A"},
              {"field":"amount","operator":"BETWEEN","value":[1000,5000]},
              {"field":"channel","operator":"IN","value":["TRANSFER","QR"]}
            ]}
            """);
        var context = new PromotionContext("TX-1","BANK_A","TRANSFER","PACS_008","LAK",
                new BigDecimal("2500"),new BigDecimal("10"),"RETAIL",Instant.now(),Map.of());
        assertTrue(evaluator.matches(rule, context));
    }

    @Test
    void rejectsUnknownFieldAndOperator() throws Exception {
        var unknownField = mapper.readTree("{\"all\":[{\"field\":\"javaExpression\",\"operator\":\"EQ\",\"value\":\"x\"}]}");
        assertThrows(IllegalArgumentException.class, () -> evaluator.matches(unknownField,
                new PromotionContext("T","P","QR","Q","LAK",BigDecimal.ONE,BigDecimal.ONE,null,Instant.now(),Map.of())));
    }
}
