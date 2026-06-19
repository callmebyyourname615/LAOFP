package com.example.switching.release;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChangeWindowGateService {
    private final JdbcTemplate jdbc;
    public ChangeWindowGateService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public boolean evaluate(String releaseReference,String environment,String changeType,String actor,OffsetDateTime at){
        List<Map<String,Object>> freezes=jdbc.queryForList("""
            SELECT id,severity,reason FROM release_freeze_period WHERE environment=? AND ? >= starts_at AND ? < ends_at ORDER BY severity DESC
            """,environment,at,at);
        boolean inWindow=Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM release_change_window WHERE environment=? AND change_type IN (?, 'ANY') AND ? >= starts_at AND ? < ends_at)
            """,Boolean.class,environment,changeType,at,at));
        boolean hardFreeze=freezes.stream().anyMatch(f->"HARD".equals(f.get("severity")));
        boolean exception=false;
        if(hardFreeze){
            exception=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM release_freeze_exception e JOIN release_freeze_period f ON f.id=e.freeze_period_id
                 WHERE e.release_reference=? AND e.status='APPROVED' AND e.expires_at>? AND f.environment=? AND ? >= f.starts_at AND ? < f.ends_at)
                """,Boolean.class,releaseReference,at,environment,at,at));
        }
        boolean allowed=inWindow && (!hardFreeze || exception);
        String reason=!inWindow?"outside approved change window":hardFreeze&&!exception?"hard freeze without approved exception":"approved change window";
        String evidence=hash(releaseReference+"|"+environment+"|"+changeType+"|"+at+"|"+allowed+"|"+reason);
        jdbc.update("INSERT INTO release_gate_decision(id,release_reference,environment,change_type,decision,reason,evaluated_by,evidence_hash) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),releaseReference,environment,changeType,allowed?"ALLOW":"DENY",reason,actor,evidence);
        if(allowed&&exception){jdbc.update("UPDATE release_freeze_exception SET status='USED' WHERE release_reference=? AND status='APPROVED'",releaseReference);}
        return allowed;
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
