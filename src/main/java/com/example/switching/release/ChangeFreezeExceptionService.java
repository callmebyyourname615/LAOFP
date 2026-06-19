package com.example.switching.release;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ChangeFreezeExceptionService {
    private final JdbcTemplate jdbc;
    public ChangeFreezeExceptionService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID request(UUID freezeId,String releaseReference,String justification,String requester,OffsetDateTime expiresAt){
        if(justification==null||justification.isBlank()) throw new IllegalArgumentException("justification is required");
        if(expiresAt==null||!expiresAt.isAfter(OffsetDateTime.now())||expiresAt.isAfter(OffsetDateTime.now().plusHours(24))) throw new IllegalArgumentException("exception expiry must be within 24 hours");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO release_freeze_exception(id,freeze_period_id,release_reference,justification,requested_by,expires_at,status) VALUES (?,?,?,?,?,?,'REQUESTED')",
                id,freezeId,releaseReference,justification,requester,expiresAt);
        return id;
    }

    @Transactional
    public void decide(UUID exceptionId,String approver,boolean approve){
        Map<String,Object> row=jdbc.queryForMap("SELECT requested_by,status,expires_at FROM release_freeze_exception WHERE id=? FOR UPDATE",exceptionId);
        if(approver.equals(row.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve freeze exception");
        if(!"REQUESTED".equals(row.get("status"))) throw new IllegalStateException("freeze exception is not pending");
        int changed=jdbc.update("UPDATE release_freeze_exception SET status=?,approved_by=? WHERE id=? AND expires_at>now()",approve?"APPROVED":"REJECTED",approver,exceptionId);
        if(changed!=1) throw new IllegalStateException("freeze exception expired before decision");
    }
}
