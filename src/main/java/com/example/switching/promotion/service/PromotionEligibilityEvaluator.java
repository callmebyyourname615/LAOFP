package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class PromotionEligibilityEvaluator {
    private static final Set<String> FIELDS=Set.of("participantId","channel","messageType","currency","amount","grossFee","customerSegment");
    private static final Set<String> OPS=Set.of("EQ","IN","GTE","LTE","BETWEEN");
    public boolean matches(JsonNode rule, PromotionContext context) {
        if (rule==null || !rule.isObject()) throw new IllegalArgumentException("Promotion rule must be an object");
        JsonNode conditions=rule.path("all");
        if (!conditions.isArray() || conditions.isEmpty()) throw new IllegalArgumentException("Promotion rule requires non-empty all[]");
        for (JsonNode c:conditions) if(!match(c,context)) return false;
        return true;
    }
    private boolean match(JsonNode c,PromotionContext context){
        String field=text(c,"field"), op=text(c,"operator").toUpperCase(Locale.ROOT);
        if(!FIELDS.contains(field)||!OPS.contains(op)) throw new IllegalArgumentException("Unsupported promotion rule field/operator");
        Object actual=value(field,context); JsonNode expected=c.get("value");
        return switch(op){
            case "EQ" -> string(actual).equalsIgnoreCase(expected.asText());
            case "IN" -> { if(!expected.isArray()) throw new IllegalArgumentException("IN requires array"); boolean found=false; for(JsonNode n:expected) found|=string(actual).equalsIgnoreCase(n.asText()); yield found; }
            case "GTE" -> decimal(actual).compareTo(expected.decimalValue())>=0;
            case "LTE" -> decimal(actual).compareTo(expected.decimalValue())<=0;
            case "BETWEEN" -> { if(!expected.isArray()||expected.size()!=2) throw new IllegalArgumentException("BETWEEN requires [min,max]"); BigDecimal a=decimal(actual); yield a.compareTo(expected.get(0).decimalValue())>=0&&a.compareTo(expected.get(1).decimalValue())<=0; }
            default -> false;
        };
    }
    private Object value(String f,PromotionContext c){return switch(f){case "participantId"->c.participantId();case "channel"->c.channel();case "messageType"->c.messageType();case "currency"->c.currency();case "amount"->c.amount();case "grossFee"->c.grossFee();case "customerSegment"->c.customerSegment();default->null;};}
    private static String text(JsonNode n,String k){JsonNode v=n.get(k);if(v==null||!v.isTextual()||v.asText().isBlank())throw new IllegalArgumentException("Missing "+k);return v.asText();}
    private static String string(Object v){return v==null?"":String.valueOf(v);}
    private static BigDecimal decimal(Object v){if(v instanceof BigDecimal b)return b;return new BigDecimal(string(v));}
}
