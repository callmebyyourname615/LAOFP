package com.example.switching.incidents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class IncidentCorrectiveActionService {
    private final JdbcTemplate jdbc;
    public IncidentCorrectiveActionService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID open(String reference,String severity,String title,String commander,OffsetDateTime detectedAt){
        if(!Set.of("SEV1","SEV2","SEV3","SEV4").contains(severity)) throw new IllegalArgumentException("invalid severity");
        UUID id=UUID.randomUUID(); String evidence=hash(reference+"|"+severity+"|"+title+"|"+commander+"|"+detectedAt);
        jdbc.update("INSERT INTO incident_record(id,incident_reference,severity,title,status,incident_commander,detected_at,evidence_hash) VALUES (?,?,?,?, 'OPEN',?,?,?)",
                id,reference,severity,title,commander,detectedAt,evidence);
        addTimeline(id,detectedAt,"DETECTED",title,commander);
        return id;
    }

    @Transactional
    public void addTimeline(UUID incidentId,OffsetDateTime eventTime,String type,String details,String actor){
        if(details==null||details.isBlank()) throw new IllegalArgumentException("timeline details required");
        String evidence=hash(incidentId+"|"+eventTime+"|"+type+"|"+details+"|"+actor);
        jdbc.update("INSERT INTO incident_timeline_event(id,incident_id,event_time,event_type,details,actor,evidence_hash) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),incidentId,eventTime,type,details,actor,evidence);
    }

    @Transactional
    public UUID addAction(UUID incidentId,String type,String priority,String description,String owner,OffsetDateTime dueAt){
        if(dueAt.isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("corrective action due date must be in the future");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO corrective_action(id,incident_id,action_type,priority,description,owner,due_at,status) VALUES (?,?,?,?,?,?,?,'OPEN')",
                id,incidentId,type,priority,description,owner,dueAt);
        jdbc.update("UPDATE incident_record SET status='ACTION_TRACKING' WHERE id=? AND status<>'CLOSED'",incidentId);
        return id;
    }


    @Transactional
    public void completeAction(UUID actionId,String actor,String evidenceHash){
        if(evidenceHash==null||!evidenceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("completion evidence hash required");
        int changed=jdbc.update("UPDATE corrective_action SET status='DONE',completed_at=now(),completion_evidence_hash=? WHERE id=? AND owner=? AND status IN ('OPEN','IN_PROGRESS','BLOCKED')",evidenceHash,actionId,actor);
        if(changed!=1) throw new IllegalStateException("action cannot be completed by this actor or state");
    }

    @Transactional
    public void approveClosure(UUID incidentId,String role,String approver,boolean approved,String comment){
        if(!Set.of("ENGINEERING","OPERATIONS","SECURITY","BUSINESS").contains(role)) throw new IllegalArgumentException("invalid closure role");
        jdbc.update("INSERT INTO incident_closure_approval(id,incident_id,approval_role,approver,decision,comment) VALUES (?,?,?,?,?,?) ON CONFLICT(incident_id,approval_role) DO UPDATE SET approver=excluded.approver,decision=excluded.decision,comment=excluded.comment,decided_at=now()",
                UUID.randomUUID(),incidentId,role,approver,approved?"APPROVED":"REJECTED",comment);
    }

    @Transactional
    public void close(UUID incidentId,String rootCause,String customerImpact){
        if(rootCause==null||rootCause.isBlank()) throw new IllegalArgumentException("root cause is required");
        Integer openActions=jdbc.queryForObject("SELECT count(*) FROM corrective_action WHERE incident_id=? AND status NOT IN ('DONE','RISK_ACCEPTED')",Integer.class,incidentId);
        if(openActions!=null&&openActions>0) throw new IllegalStateException("all corrective actions must be completed or risk accepted");
        Integer approvals=jdbc.queryForObject("SELECT count(DISTINCT approval_role) FROM incident_closure_approval WHERE incident_id=? AND decision='APPROVED'",Integer.class,incidentId);
        if(approvals==null||approvals<3) throw new IllegalStateException("at least three independent closure roles must approve");
        jdbc.update("UPDATE incident_record SET status='CLOSED',root_cause=?,customer_impact=?,closed_at=now(),evidence_hash=? WHERE id=? AND status<>'CLOSED'",
                rootCause,customerImpact,hash(incidentId+"|"+rootCause+"|"+customerImpact),incidentId);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
