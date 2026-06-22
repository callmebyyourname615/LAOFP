package com.example.switching.fees;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import com.example.switching.promotion.service.PromotionApplicationService;
import com.example.switching.promotion.service.PromotionContext;

@Service
public class FeeAssessmentService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<PromotionApplicationService> promotionApplications;
    public FeeAssessmentService(JdbcTemplate jdbc,ObjectProvider<PromotionApplicationService> promotionApplications){this.jdbc=jdbc;this.promotionApplications=promotionApplications;}

    @Transactional
    public FeeAssessmentResult assess(String transactionReference,String participantCode,String messageType,String currency,BigDecimal amount){
        if(amount==null||amount.signum()<0) throw new IllegalArgumentException("amount must be non-negative");
        Map<String,Object> rule=jdbc.queryForMap("""
            SELECT tv.id AS version_id,tr.id AS rule_id,tr.flat_fee,tr.rate_basis_points,tr.minimum_fee,tr.maximum_fee
              FROM tariff_plan tp JOIN tariff_version tv ON tv.plan_id=tp.id
              JOIN tariff_rule tr ON tr.tariff_version_id=tv.id
             WHERE tv.status='ACTIVE' AND now()>=tv.valid_from AND (tv.valid_until IS NULL OR now()<tv.valid_until)
               AND (tp.participant_code IS NULL OR tp.participant_code=?)
               AND tr.message_type=? AND tr.currency=?
               AND ? >= tr.minimum_amount AND (tr.maximum_amount IS NULL OR ? <= tr.maximum_amount)
             ORDER BY (tp.participant_code IS NOT NULL) DESC,tr.priority ASC LIMIT 1
            """,participantCode,messageType,currency,amount,amount);
        BigDecimal fee=calculate(amount,(BigDecimal)rule.get("flat_fee"),(BigDecimal)rule.get("rate_basis_points"),
                (BigDecimal)rule.get("minimum_fee"),(BigDecimal)rule.get("maximum_fee"));
        UUID version=(UUID)rule.get("version_id"), ruleId=(UUID)rule.get("rule_id");
        String hash=hash(transactionReference+"|"+version+"|"+ruleId+"|"+amount.toPlainString()+"|"+fee.toPlainString()+"|"+currency);
        jdbc.update("""
            INSERT INTO fee_assessment(id,transaction_reference,tariff_version_id,tariff_rule_id,amount,assessed_fee,currency,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(transaction_reference) DO NOTHING
            """,UUID.randomUUID(),transactionReference,version,ruleId,amount,fee,currency,hash);
        var service=promotionApplications.getIfAvailable();
        if(service==null) return FeeAssessmentResult.withoutPromotions(version,ruleId,fee,currency);
        var promotions=service.apply(new PromotionContext(transactionReference,participantCode,messageType,messageType,currency,amount,fee,null,java.time.Instant.now(),java.util.Map.of()));
        BigDecimal discount=promotions.stream().map(com.example.switching.promotion.dto.PromotionApplicationView::discountAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new FeeAssessmentResult(version,ruleId,fee,discount,fee.subtract(discount).max(BigDecimal.ZERO),currency,List.copyOf(promotions));
    }

    public static BigDecimal calculate(BigDecimal amount,BigDecimal flat,BigDecimal basisPoints,BigDecimal minimum,BigDecimal maximum){
        BigDecimal proportional=amount.multiply(basisPoints).divide(new BigDecimal("10000"),4,RoundingMode.HALF_UP);
        BigDecimal fee=flat.add(proportional).max(minimum).setScale(4,RoundingMode.HALF_UP);
        return maximum==null?fee:fee.min(maximum);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
