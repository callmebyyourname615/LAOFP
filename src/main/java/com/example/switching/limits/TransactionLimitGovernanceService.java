package com.example.switching.limits;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionLimitGovernanceService {
    private final JdbcTemplate jdbc;

    public TransactionLimitGovernanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LimitDecision authorizeAndConsume(String transactionReference, String participantCode, String productCode,
                                             String channel, String currency, BigDecimal amount, OffsetDateTime submittedAt) {
        require(transactionReference, "transactionReference"); require(participantCode, "participantCode");
        require(productCode, "productCode"); require(channel, "channel");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be ISO-4217 uppercase");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        OffsetDateTime now = submittedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : submittedAt;
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS locked",Integer.class,"limit-tx|"+transactionReference);
        List<Map<String,Object>> prior=jdbc.queryForList("""
            SELECT decision,reason,evidence_hash FROM transaction_limit_decision_audit
             WHERE transaction_reference=? AND decision IN ('ALLOW','DENY') ORDER BY decided_at DESC LIMIT 1
            """,transactionReference);
        if(!prior.isEmpty()){
            Map<String,Object> result=prior.get(0); String decision=String.valueOf(result.get("decision"));
            return new LimitDecision("ALLOW".equals(decision),decision,String.valueOf(result.get("reason")),String.valueOf(result.get("evidence_hash")));
        }

        Integer entitlement = jdbc.queryForObject("""
            SELECT count(*) FROM participant_product_entitlement
             WHERE participant_code=? AND product_code=? AND channel=? AND currency=? AND status='ACTIVE'
               AND effective_from<=? AND (effective_until IS NULL OR effective_until>?)
            """, Integer.class, participantCode, productCode, channel, currency, now, now);
        if (entitlement == null || entitlement == 0) return deny(transactionReference, participantCode, amount, null, "product entitlement is not active");

        List<Map<String,Object>> policies = jdbc.queryForList("""
            SELECT * FROM transaction_limit_policy
             WHERE status='ACTIVE' AND product_code=? AND channel=? AND currency=?
               AND effective_from<=? AND (effective_until IS NULL OR effective_until>?)
               AND ((scope_type='SYSTEM' AND scope_value='*')
                 OR (scope_type='PARTICIPANT' AND scope_value=?)
                 OR (scope_type='PRODUCT' AND scope_value=?)
                 OR (scope_type='PARTICIPANT_PRODUCT' AND scope_value=?))
             ORDER BY CASE scope_type WHEN 'PARTICIPANT_PRODUCT' THEN 1 WHEN 'PARTICIPANT' THEN 2 WHEN 'PRODUCT' THEN 3 ELSE 4 END
            """, productCode, channel, currency, now, now, participantCode, productCode, participantCode + ":" + productCode);
        if (policies.isEmpty()) return deny(transactionReference, participantCode, amount, null, "no active transaction limit policy");

        List<PendingConsumption> pendingConsumptions=new java.util.ArrayList<>();
        List<UUID> pendingOverrides=new java.util.ArrayList<>();
        for (Map<String,Object> policy : policies) {
            UUID policyId=(UUID)policy.get("id");
            BigDecimal perTx=(BigDecimal)policy.get("per_transaction_amount");
            if (amount.compareTo(perTx)>0) {
                UUID overrideId=findApprovedOverride(policyId,participantCode,transactionReference,amount);
                if(overrideId==null) return deny(transactionReference,participantCode,amount,policyId,"per-transaction limit exceeded");
                pendingOverrides.add(overrideId);
            }
            ZoneId zone=ZoneId.of(String.valueOf(policy.get("timezone")));
            BigDecimal hourly=(BigDecimal)policy.get("hourly_amount");
            BigDecimal daily=(BigDecimal)policy.get("daily_amount");
            Long dailyCount=policy.get("daily_count")==null?null:((Number)policy.get("daily_count")).longValue();
            if (hourly!=null) {
                PendingConsumption pending=prepareWindow(policyId,participantCode,productCode,currency,"HOUR",hourWindow(now,zone),amount,hourly,null);
                if(pending==null) return deny(transactionReference,participantCode,amount,policyId,"hourly amount limit exceeded");
                pendingConsumptions.add(pending);
            }
            if (daily!=null || dailyCount!=null) {
                PendingConsumption pending=prepareWindow(policyId,participantCode,productCode,currency,"DAY",dayWindow(now,zone),amount,daily,dailyCount);
                if(pending==null) return deny(transactionReference,participantCode,amount,policyId,"daily amount/count limit exceeded");
                pendingConsumptions.add(pending);
            }
        }
        pendingConsumptions.forEach(this::applyConsumption);
        for(UUID overrideId:pendingOverrides){
            int used=jdbc.update("UPDATE transaction_limit_override_request SET status='USED',used_at=now() WHERE id=? AND status='APPROVED' AND expires_at>now()",overrideId);
            if(used!=1) throw new IllegalStateException("approved override was consumed concurrently");
            Map<String,Object> override=jdbc.queryForMap("SELECT policy_id,requested_amount FROM transaction_limit_override_request WHERE id=?",overrideId);
            String overrideEvidence=ControlEvidence.sha256(transactionReference,participantCode,override.get("policy_id"),override.get("requested_amount"),overrideId,"OVERRIDE");
            jdbc.update("INSERT INTO transaction_limit_decision_audit(id,transaction_reference,participant_code,policy_id,decision,amount,reason,evidence_hash) VALUES (?,?,?,?, 'OVERRIDE',?,'approved limit override consumed',?)",
                    UUID.randomUUID(),transactionReference,participantCode,override.get("policy_id"),amount,overrideEvidence);
        }
        String evidence=ControlEvidence.sha256(transactionReference,participantCode,productCode,channel,currency,amount,now,"ALLOW",pendingOverrides);
        jdbc.update("INSERT INTO transaction_limit_decision_audit(id,transaction_reference,participant_code,decision,amount,reason,evidence_hash) VALUES (?,?,?,'ALLOW',?,'all active policies passed',?)",
                UUID.randomUUID(),transactionReference,participantCode,amount,evidence);
        return new LimitDecision(true,"ALLOW","all active policies passed",evidence);
    }

    @Transactional
    public UUID requestOverride(UUID policyId,String participantCode,String transactionReference,BigDecimal amount,String reason,String actor,OffsetDateTime expiresAt){
        if(expiresAt==null||!expiresAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("override expiry must be in the future");
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(policyId,participantCode,transactionReference,amount,reason,actor,expiresAt);
        jdbc.update("""
            INSERT INTO transaction_limit_override_request(id,policy_id,participant_code,transaction_reference,requested_amount,reason,requested_by,expires_at,status,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,'REQUESTED',?)
            """,id,policyId,participantCode,transactionReference,amount,reason,actor,expiresAt,evidence);
        return id;
    }

    @Transactional
    public void approveOverride(UUID requestId,String approver,boolean approved){
        int changed=jdbc.update("""
            UPDATE transaction_limit_override_request SET approved_by=?,status=?
             WHERE id=? AND status='REQUESTED' AND requested_by<>? AND expires_at>now()
            """,approver,approved?"APPROVED":"REJECTED",requestId,approver);
        if(changed!=1) throw new IllegalStateException("override cannot be approved in current state or by requester");
    }

    private UUID findApprovedOverride(UUID policyId,String participant,String reference,BigDecimal amount){
        List<UUID> ids=jdbc.query("""
            SELECT id FROM transaction_limit_override_request
             WHERE policy_id=? AND participant_code=? AND transaction_reference=? AND status='APPROVED'
               AND expires_at>now() AND requested_amount>=? FOR UPDATE
            """,(rs,n)->rs.getObject(1,UUID.class),policyId,participant,reference,amount);
        return ids.isEmpty()?null:ids.get(0);
    }

    private PendingConsumption prepareWindow(UUID policyId,String participant,String product,String currency,String type,Window window,
                                             BigDecimal amount,BigDecimal maxAmount,Long maxCount){
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS locked",Integer.class,
                policyId+"|"+participant+"|"+type+"|"+window.start());
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT consumed_amount,consumed_count FROM transaction_limit_consumption
             WHERE policy_id=? AND participant_code=? AND window_type=? AND window_start=? FOR UPDATE
            """,policyId,participant,type,window.start());
        BigDecimal currentAmount=rows.isEmpty()?BigDecimal.ZERO:(BigDecimal)rows.get(0).get("consumed_amount");
        long currentCount=rows.isEmpty()?0L:((Number)rows.get(0).get("consumed_count")).longValue();
        BigDecimal nextAmount=currentAmount.add(amount); long nextCount=currentCount+1;
        if(maxAmount!=null&&nextAmount.compareTo(maxAmount)>0) return null;
        if(maxCount!=null&&nextCount>maxCount) return null;
        return new PendingConsumption(policyId,participant,product,currency,type,window,nextAmount,nextCount);
    }

    private void applyConsumption(PendingConsumption pending){
        jdbc.update("""
            INSERT INTO transaction_limit_consumption(policy_id,participant_code,product_code,currency,window_type,window_start,window_end,consumed_amount,consumed_count,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,now())
            ON CONFLICT(policy_id,participant_code,window_type,window_start)
            DO UPDATE SET consumed_amount=excluded.consumed_amount,consumed_count=excluded.consumed_count,updated_at=now()
            """,pending.policyId(),pending.participant(),pending.product(),pending.currency(),pending.type(),pending.window().start(),pending.window().end(),pending.amount(),pending.count());
    }

    private LimitDecision deny(String reference,String participant,BigDecimal amount,UUID policy,String reason){
        String evidence=ControlEvidence.sha256(reference,participant,policy,amount,"DENY",reason);
        jdbc.update("INSERT INTO transaction_limit_decision_audit(id,transaction_reference,participant_code,policy_id,decision,amount,reason,evidence_hash) VALUES (?,?,?,?, 'DENY',?,?,?)",
                UUID.randomUUID(),reference,participant,policy,amount,reason,evidence);
        return new LimitDecision(false,"DENY",reason,evidence);
    }

    public static Window hourWindow(OffsetDateTime value,ZoneId zone){
        ZonedDateTime start=value.atZoneSameInstant(zone).truncatedTo(ChronoUnit.HOURS);
        return new Window(start.toOffsetDateTime(),start.plusHours(1).toOffsetDateTime());
    }
    public static Window dayWindow(OffsetDateTime value,ZoneId zone){
        ZonedDateTime start=value.atZoneSameInstant(zone).toLocalDate().atStartOfDay(zone);
        return new Window(start.toOffsetDateTime(),start.plusDays(1).toOffsetDateTime());
    }
    private static void require(String value,String name){if(value==null||value.isBlank()) throw new IllegalArgumentException(name+" is required");}
    private record PendingConsumption(UUID policyId,String participant,String product,String currency,String type,Window window,BigDecimal amount,long count){}
    public record Window(OffsetDateTime start,OffsetDateTime end){}
    public record LimitDecision(boolean allowed,String decision,String reason,String evidenceHash){}
}
