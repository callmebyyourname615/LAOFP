package com.example.switching.liquidity.control;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class LiquidityReservationControlService {
    private final JdbcTemplate jdbc;
    public LiquidityReservationControlService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UUID reserve(String reference,String participant,String currency,BigDecimal amount,Duration ttl){
        if(reference==null||reference.isBlank()) throw new IllegalArgumentException("reference is required");
        if(amount==null||amount.signum()<=0) throw new IllegalArgumentException("amount must be positive");
        if(ttl==null||ttl.isNegative()||ttl.isZero()||ttl.compareTo(Duration.ofHours(24))>0) throw new IllegalArgumentException("ttl must be between 1 second and 24 hours");
        Integer updated=jdbc.update("""
            UPDATE participant_liquidity_control
               SET reserved_balance=reserved_balance+?, version=version+1, updated_at=now()
             WHERE participant_code=? AND currency=?
               AND available_balance-reserved_balance-? >= minimum_operating_balance
            """,amount,participant,currency,amount);
        if(updated!=1) throw new IllegalStateException("insufficient prefunded liquidity or participant liquidity control missing");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO liquidity_fund_reservation(id,reservation_reference,participant_code,currency,amount,status,expires_at)
            VALUES (?,?,?,?,?,'RESERVED',?)
            """,id,reference,participant,currency,amount, OffsetDateTime.now().plus(ttl));
        return id;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void release(String reference,String terminalStatus){
        if(!terminalStatus.equals("RELEASED")&&!terminalStatus.equals("EXPIRED")) throw new IllegalArgumentException("invalid terminal status");
        Map<String,Object> row=jdbc.queryForMap("""
            SELECT id,participant_code,currency,amount,status FROM liquidity_fund_reservation
             WHERE reservation_reference=? FOR UPDATE
            """,reference);
        if(!"RESERVED".equals(row.get("status"))) return;
        BigDecimal amount=(BigDecimal)row.get("amount");
        int changed=jdbc.update("""
            UPDATE participant_liquidity_control
               SET reserved_balance=reserved_balance-?,version=version+1,updated_at=now()
             WHERE participant_code=? AND currency=? AND reserved_balance>=?
            """,amount,row.get("participant_code"),row.get("currency"),amount);
        if(changed!=1) throw new IllegalStateException("liquidity control invariant violation");
        jdbc.update("UPDATE liquidity_fund_reservation SET status=?,completed_at=now() WHERE id=?",terminalStatus,row.get("id"));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void settle(String reference){
        Map<String,Object> row=jdbc.queryForMap("""
            SELECT id,participant_code,currency,amount,status FROM liquidity_fund_reservation
             WHERE reservation_reference=? FOR UPDATE
            """,reference);
        if("SETTLED".equals(row.get("status"))) return;
        if(!"RESERVED".equals(row.get("status"))) throw new IllegalStateException("reservation is not active");
        BigDecimal amount=(BigDecimal)row.get("amount");
        int changed=jdbc.update("""
            UPDATE participant_liquidity_control
               SET available_balance=available_balance-?,reserved_balance=reserved_balance-?,version=version+1,updated_at=now()
             WHERE participant_code=? AND currency=? AND available_balance>=? AND reserved_balance>=?
            """,amount,amount,row.get("participant_code"),row.get("currency"),amount,amount);
        if(changed!=1) throw new IllegalStateException("liquidity settlement invariant violation");
        jdbc.update("UPDATE liquidity_fund_reservation SET status='SETTLED',completed_at=now() WHERE id=?",row.get("id"));
    }
}
