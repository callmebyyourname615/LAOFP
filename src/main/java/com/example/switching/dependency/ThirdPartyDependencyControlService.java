package com.example.switching.dependency;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ThirdPartyDependencyControlService {
    private final JdbcTemplate jdbc;
    public ThirdPartyDependencyControlService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CircuitDecision recordSample(String dependencyCode,OffsetDateTime observedAt,boolean success,Integer latencyMs,String responseClass){
        Map<String,Object> dependency=jdbc.queryForMap("SELECT id FROM third_party_dependency WHERE dependency_code=? AND enabled=true",dependencyCode);
        UUID dependencyId=(UUID)dependency.get("id");
        String evidence=ControlEvidence.sha256(dependencyCode,observedAt,success,latencyMs,responseClass);
        jdbc.update("INSERT INTO third_party_health_sample(id,dependency_id,observed_at,success,latency_ms,response_class,evidence_hash) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(),dependencyId,observedAt,success,latencyMs,responseClass,evidence);
        Map<String,Object> policy=jdbc.queryForMap("SELECT * FROM third_party_sla_policy WHERE dependency_id=? AND status='ACTIVE'",dependencyId);
        int failureThreshold=((Number)policy.get("failure_threshold")).intValue();
        int recoveryThreshold=((Number)policy.get("recovery_success_threshold")).intValue();
        int openSeconds=((Number)policy.get("open_seconds")).intValue();
        jdbc.update("UPDATE third_party_override_request SET status='EXPIRED' WHERE dependency_id=? AND status='APPROVED' AND expires_at<=now()",dependencyId);
        Map<String,Object> state=ensureState(dependencyId);
        String current=String.valueOf(state.get("state"));
        if(current.startsWith("FORCED_")){
            Integer activeOverride=jdbc.queryForObject("SELECT count(*) FROM third_party_override_request WHERE dependency_id=? AND status='APPROVED' AND expires_at>now()",Integer.class,dependencyId);
            if(activeOverride!=null&&activeOverride>0) return new CircuitDecision(current,"manual override active");
            current="CLOSED";
            jdbc.update("UPDATE third_party_circuit_state SET state='CLOSED',reason='manual override expired',updated_at=now() WHERE dependency_id=?",dependencyId);
        }
        int failures=((Number)state.get("consecutive_failures")).intValue();
        int successes=((Number)state.get("consecutive_successes")).intValue();
        String next=current; String reason;
        if(success){failures=0;successes++;
            if("HALF_OPEN".equals(current)&&successes>=recoveryThreshold) next="CLOSED";
            else if("OPEN".equals(current)&&canProbe(state)) next="HALF_OPEN";
            reason="successful health sample";
        }else{successes=0;failures++;
            if(failures>=failureThreshold) next="OPEN";
            reason="failed health sample";
        }
        OffsetDateTime opened="OPEN".equals(next)?OffsetDateTime.now():null;
        OffsetDateTime probe="OPEN".equals(next)?OffsetDateTime.now().plusSeconds(openSeconds):null;
        jdbc.update("""
            UPDATE third_party_circuit_state SET state=?,consecutive_failures=?,consecutive_successes=?,opened_at=?,next_probe_at=?,reason=?,updated_at=now()
             WHERE dependency_id=?
            """,next,failures,successes,opened,probe,reason,dependencyId);
        return new CircuitDecision(next,reason);
    }

    @Transactional
    public UUID requestOverride(String dependencyCode,String requestedState,String reason,String requester,OffsetDateTime expiresAt){
        if(expiresAt==null||!expiresAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("override expiry must be in the future");
        UUID dependencyId=jdbc.queryForObject("SELECT id FROM third_party_dependency WHERE dependency_code=?",UUID.class,dependencyCode);
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO third_party_override_request(id,dependency_id,requested_state,reason,requested_by,expires_at,status) VALUES (?,?,?,?,?,?,'REQUESTED')",
                id,dependencyId,requestedState,reason,requester,expiresAt);
        return id;
    }

    @Transactional
    public void approveOverride(UUID requestId,String approver,boolean approved){
        Map<String,Object> request=jdbc.queryForMap("SELECT * FROM third_party_override_request WHERE id=? FOR UPDATE",requestId);
        if(approver.equals(request.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve dependency override");
        if(!"REQUESTED".equals(request.get("status"))||toOffsetDateTime(request.get("expires_at")).isBefore(OffsetDateTime.now())) throw new IllegalStateException("override request is not pending or has expired");
        jdbc.update("UPDATE third_party_override_request SET approved_by=?,status=? WHERE id=?",approver,approved?"APPROVED":"REJECTED",requestId);
        if(approved){
            UUID dependencyId=(UUID)request.get("dependency_id"); ensureState(dependencyId);
            jdbc.update("UPDATE third_party_circuit_state SET state=?,reason=?,updated_at=now() WHERE dependency_id=?",
                    request.get("requested_state"),"approved override "+requestId,dependencyId);
        }
    }

    public AvailabilityWindow availability(String dependencyCode,OffsetDateTime from){
        UUID id=jdbc.queryForObject("SELECT id FROM third_party_dependency WHERE dependency_code=?",UUID.class,dependencyCode);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT success,latency_ms FROM third_party_health_sample WHERE dependency_id=? AND observed_at>=?",id,from);
        if(rows.isEmpty()) return new AvailabilityWindow(0d,0d,0);
        long success=rows.stream().filter(r->Boolean.TRUE.equals(r.get("success"))).count();
        List<Integer> latencies=rows.stream().map(r->(Integer)r.get("latency_ms")).filter(v->v!=null).sorted().toList();
        double p95=latencies.isEmpty()?0:latencies.get(Math.min(latencies.size()-1,(int)Math.ceil(latencies.size()*0.95)-1));
        return new AvailabilityWindow((double)success/rows.size(),p95,rows.size());
    }

    private Map<String,Object> ensureState(UUID dependencyId){
        jdbc.update("INSERT INTO third_party_circuit_state(dependency_id,state,reason) VALUES (?,'CLOSED','initial state') ON CONFLICT DO NOTHING",dependencyId);
        return jdbc.queryForMap("SELECT * FROM third_party_circuit_state WHERE dependency_id=? FOR UPDATE",dependencyId);
    }
    private static OffsetDateTime toOffsetDateTime(Object value){
        if(value instanceof OffsetDateTime time) return time;
        if(value instanceof java.sql.Timestamp time) return time.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(String.valueOf(value));
    }
    private static boolean canProbe(Map<String,Object> state){
        Object probe=state.get("next_probe_at");
        if(probe==null) return true;
        OffsetDateTime time=toOffsetDateTime(probe);
        return time.isBefore(OffsetDateTime.now());
    }
    public record CircuitDecision(String state,String reason){}
    public record AvailabilityWindow(double availability,double p95LatencyMs,int samples){}
}
