package com.example.switching.finality;

import com.example.switching.governance.ControlEvidence;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentFinalityService {
    private final JdbcTemplate jdbc;
    public PaymentFinalityService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public IdempotencyClaim claim(String participant,String key,String requestHash,OffsetDateTime expiresAt){
        ControlEvidence.requireSha256(requestHash,"requestHash");
        if(expiresAt==null||!expiresAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("idempotency expiry must be in the future");
        UUID id=UUID.randomUUID();
        try{
            jdbc.update("INSERT INTO payment_idempotency_record(id,participant_code,idempotency_key,request_hash,status,expires_at) VALUES (?,?,?,?,'CLAIMED',?)",
                    id,participant,key,requestHash,expiresAt);
            return new IdempotencyClaim(id,true,"CLAIMED",null);
        }catch(DuplicateKeyException duplicate){
            Map<String,Object> existing=jdbc.queryForMap("SELECT id,request_hash,status,transaction_reference,expires_at FROM payment_idempotency_record WHERE participant_code=? AND idempotency_key=? FOR UPDATE",participant,key);
            if(!requestHash.equals(existing.get("request_hash"))) throw new IllegalStateException("idempotency key reused with different request payload");
            return new IdempotencyClaim((UUID)existing.get("id"),false,String.valueOf(existing.get("status")),(String)existing.get("transaction_reference"));
        }
    }

    @Transactional
    public void completeClaim(UUID claimId,String transactionReference,String responseHash){
        ControlEvidence.requireSha256(responseHash,"responseHash");
        int changed=jdbc.update("""
            UPDATE payment_idempotency_record SET status='COMPLETED',transaction_reference=?,response_hash=?,completed_at=now()
             WHERE id=? AND status='CLAIMED' AND expires_at>now()
            """,transactionReference,responseHash,claimId);
        if(changed!=1) throw new IllegalStateException("idempotency claim is not active");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean registerDuplicateFingerprint(String participant,String product,String transactionReference,
                                                String sourceAccountHash,String destinationAccountHash,
                                                BigDecimal amount,String currency,LocalDate businessDate,
                                                long timeBucket,OffsetDateTime expiresAt){
        ControlEvidence.requireSha256(sourceAccountHash,"sourceAccountHash");
        ControlEvidence.requireSha256(destinationAccountHash,"destinationAccountHash");
        if(amount==null||amount.signum()<=0) throw new IllegalArgumentException("amount must be positive");
        if(expiresAt==null||!expiresAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("fingerprint expiry must be in the future");
        String stableFingerprint=duplicateFingerprint(participant,product,sourceAccountHash,destinationAccountHash,amount,currency,businessDate,timeBucket);
        try{
            jdbc.update("""
                INSERT INTO payment_duplicate_fingerprint(fingerprint,participant_code,product_code,transaction_reference,amount,currency,expires_at)
                VALUES (?,?,?,?,?,?,?)
                """,stableFingerprint,participant,product,transactionReference,amount,currency,expiresAt);
            return true;
        }catch(DuplicateKeyException duplicate){return false;}
    }

    public static String duplicateFingerprint(String participant,String product,String sourceAccountHash,String destinationAccountHash,
                                              BigDecimal amount,String currency,LocalDate businessDate,long timeBucket){
        if(participant==null||participant.isBlank()||product==null||product.isBlank()||currency==null||currency.length()!=3||businessDate==null) throw new IllegalArgumentException("canonical fingerprint fields are required");
        ControlEvidence.requireSha256(sourceAccountHash,"sourceAccountHash");
        ControlEvidence.requireSha256(destinationAccountHash,"destinationAccountHash");
        return ControlEvidence.sha256(participant.trim().toUpperCase(),product.trim().toUpperCase(),sourceAccountHash,destinationAccountHash,
                amount.stripTrailingZeros().toPlainString(),currency.trim().toUpperCase(),businessDate,timeBucket);
    }

    @Transactional
    public UUID recordProvisional(String transactionReference,String participant,String reason){
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(transactionReference,participant,"PROVISIONAL",reason);
        jdbc.update("INSERT INTO payment_finality_record(id,transaction_reference,participant_code,finality_status,finality_reason,evidence_hash) VALUES (?,?,?,'PROVISIONAL',?,?)",
                id,transactionReference,participant,reason,evidence);
        return id;
    }

    @Transactional
    public void finalizePayment(String transactionReference,String reason){
        int changed=jdbc.update("UPDATE payment_finality_record SET finality_status='FINAL',finality_reason=?,finalized_at=now() WHERE transaction_reference=? AND finality_status='PROVISIONAL'",reason,transactionReference);
        if(changed!=1) throw new IllegalStateException("payment is not provisional or is already final");
    }

    @Transactional
    public UUID requestReversal(String transactionReference,String reversalReference,String reasonCode,String detail,
                                String requester,OffsetDateTime expiresAt){
        String evidence=ControlEvidence.sha256(transactionReference,reversalReference,reasonCode,detail,requester,expiresAt);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO payment_reversal_request(id,transaction_reference,reversal_reference,reason_code,reason_detail,requested_by,status,expires_at,evidence_hash)
            SELECT ?,?,?,?,?,?,'REQUESTED',?,? FROM payment_finality_record
             WHERE transaction_reference=? AND finality_status='FINAL'
            """,id,transactionReference,reversalReference,reasonCode,detail,requester,expiresAt,evidence,transactionReference);
        Integer exists=jdbc.queryForObject("SELECT count(*) FROM payment_reversal_request WHERE id=?",Integer.class,id);
        if(exists==null||exists==0) throw new IllegalStateException("only a final payment can enter reversal workflow");
        return id;
    }

    @Transactional
    public void approveReversal(UUID requestId,String role,String actor){
        if(!role.equals("OPERATIONS")&&!role.equals("RISK")) throw new IllegalArgumentException("role must be OPERATIONS or RISK");
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM payment_reversal_request WHERE id=? FOR UPDATE",requestId);
        if(!String.valueOf(row.get("status")).matches("REQUESTED|PARTIALLY_APPROVED")) throw new IllegalStateException("reversal request is not approvable");
        if(actor.equals(row.get("requested_by"))||actor.equals(row.get("operations_approved_by"))||actor.equals(row.get("risk_approved_by"))) throw new IllegalArgumentException("approval actors must be independent");
        String column=role.equals("OPERATIONS")?"operations_approved_by":"risk_approved_by";
        jdbc.update("UPDATE payment_reversal_request SET "+column+"=? WHERE id=?",actor,requestId);
        jdbc.update("""
            UPDATE payment_reversal_request SET status=CASE WHEN operations_approved_by IS NOT NULL AND risk_approved_by IS NOT NULL THEN 'APPROVED' ELSE 'PARTIALLY_APPROVED' END
             WHERE id=? AND expires_at>now()
            """,requestId);
    }

    @Transactional
    public void executeReversal(UUID requestId,String executor){
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM payment_reversal_request WHERE id=? FOR UPDATE",requestId);
        if(!"APPROVED".equals(row.get("status"))||toOffsetDateTime(row.get("expires_at")).isBefore(OffsetDateTime.now())) throw new IllegalStateException("reversal is not approved or has expired");
        if(executor.equals(row.get("requested_by"))||executor.equals(row.get("operations_approved_by"))||executor.equals(row.get("risk_approved_by"))) throw new IllegalArgumentException("executor must be independent");
        int finalityChanged=jdbc.update("UPDATE payment_finality_record SET finality_status='REVERSED_BY_EXCEPTION',finality_reason=? WHERE transaction_reference=? AND finality_status='FINAL'",
                "approved reversal "+row.get("reversal_reference"),row.get("transaction_reference"));
        if(finalityChanged!=1) throw new IllegalStateException("finality state changed concurrently");
        jdbc.update("UPDATE payment_reversal_request SET status='EXECUTED',executed_at=now() WHERE id=? AND status='APPROVED'",requestId);
    }
    private static OffsetDateTime toOffsetDateTime(Object value){
        if(value instanceof OffsetDateTime time) return time;
        if(value instanceof java.sql.Timestamp time) return time.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(String.valueOf(value));
    }
    public record IdempotencyClaim(UUID claimId,boolean newlyClaimed,String status,String transactionReference){}
}
