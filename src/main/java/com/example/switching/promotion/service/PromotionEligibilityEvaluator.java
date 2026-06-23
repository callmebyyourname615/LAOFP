package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evaluates the deliberately small, data-only promotion eligibility DSL.
 *
 * <p>The evaluator never executes SpEL, Java methods or user-provided code. Rules are limited to
 * an allow-list of payment fields and comparison operators. This makes a maker-checker approved
 * rule safe to evaluate in the payment path.</p>
 */
@Component
public class PromotionEligibilityEvaluator {
    private static final int MAX_CONDITIONS = 32;
    private static final int MAX_IN_VALUES = 64;
    private static final int MAX_TEXT_LENGTH = 256;

    private static final Set<String> FIELDS = Set.of(
            "participantId", "channel", "messageType", "currency",
            "amount", "grossFee", "customerSegment");
    private static final Set<String> OPERATORS = Set.of("EQ", "IN", "GTE", "LTE", "BETWEEN");

    public boolean matches(JsonNode rule, PromotionContext context) {
        if (rule == null || !rule.isObject()) {
            throw new IllegalArgumentException("Promotion rule must be an object");
        }
        if (context == null) {
            throw new IllegalArgumentException("Promotion context is required");
        }
        JsonNode conditions = rule.path("all");
        if (!conditions.isArray() || conditions.isEmpty()) {
            throw new IllegalArgumentException("Promotion rule requires non-empty all[]");
        }
        if (conditions.size() > MAX_CONDITIONS) {
            throw new IllegalArgumentException("Promotion rule exceeds the condition limit");
        }
        for (JsonNode condition : conditions) {
            if (!match(condition, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean match(JsonNode condition, PromotionContext context) {
        if (condition == null || !condition.isObject()) {
            throw new IllegalArgumentException("Promotion condition must be an object");
        }
        String field = requiredText(condition, "field");
        String operator = requiredText(condition, "operator").toUpperCase(Locale.ROOT);
        if (!FIELDS.contains(field) || !OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported promotion rule field/operator");
        }

        Object actual = fieldValue(field, context);
        JsonNode expected = condition.get("value");
        if (expected == null || expected.isNull()) {
            throw new IllegalArgumentException("Missing value");
        }
        if (actual == null) {
            return false;
        }

        return switch (operator) {
            case "EQ" -> equalsValue(actual, expected);
            case "IN" -> inValues(actual, expected);
            case "GTE" -> decimal(actual).compareTo(requiredNumber(expected, "GTE")) >= 0;
            case "LTE" -> decimal(actual).compareTo(requiredNumber(expected, "LTE")) <= 0;
            case "BETWEEN" -> between(actual, expected);
            default -> throw new IllegalArgumentException("Unsupported promotion operator");
        };
    }

    private boolean equalsValue(Object actual, JsonNode expected) {
        if (!expected.isValueNode() || expected.isContainerNode()) {
            throw new IllegalArgumentException("EQ requires a scalar value");
        }
        if (actual instanceof BigDecimal) {
            return decimal(actual).compareTo(requiredNumber(expected, "EQ")) == 0;
        }
        return limitedText(actual).equalsIgnoreCase(limitedText(expected.asText()));
    }

    private boolean inValues(Object actual, JsonNode expected) {
        if (!expected.isArray() || expected.isEmpty() || expected.size() > MAX_IN_VALUES) {
            throw new IllegalArgumentException("IN requires a non-empty bounded array");
        }
        String actualText = limitedText(actual);
        for (JsonNode candidate : expected) {
            if (!candidate.isValueNode() || candidate.isContainerNode()) {
                throw new IllegalArgumentException("IN values must be scalar");
            }
            if (actualText.equalsIgnoreCase(limitedText(candidate.asText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean between(Object actual, JsonNode expected) {
        if (!expected.isArray() || expected.size() != 2) {
            throw new IllegalArgumentException("BETWEEN requires [min,max]");
        }
        BigDecimal minimum = requiredNumber(expected.get(0), "BETWEEN");
        BigDecimal maximum = requiredNumber(expected.get(1), "BETWEEN");
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("BETWEEN minimum must not exceed maximum");
        }
        BigDecimal value = decimal(actual);
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private Object fieldValue(String field, PromotionContext context) {
        return switch (field) {
            case "participantId" -> context.participantId();
            case "channel" -> context.channel();
            case "messageType" -> context.messageType();
            case "currency" -> context.currency();
            case "amount" -> context.amount();
            case "grossFee" -> context.grossFee();
            case "customerSegment" -> context.customerSegment();
            default -> throw new IllegalArgumentException("Unsupported promotion field");
        };
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return limitedText(value.asText());
    }

    private static String limitedText(Object value) {
        String text = String.valueOf(value);
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Promotion rule text value is too long");
        }
        return text;
    }

    private static BigDecimal requiredNumber(JsonNode value, String operator) {
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(operator + " requires a numeric value");
        }
        return value.decimalValue();
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new IllegalArgumentException("Numeric promotion operator used with a non-numeric field");
    }
}
