package com.example.switching.capacity;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CapacityGovernanceService {
    private final JdbcTemplate jdbc;
    public CapacityGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID recordObservation(String component,String environment,OffsetDateTime observedAt,BigDecimal requestRate,
                                  BigDecimal cpu,BigDecimal memory,BigDecimal p95,BigDecimal errorRate,int replicas){
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(component,environment,observedAt,requestRate,cpu,memory,p95,errorRate,replicas);
        jdbc.update("""
            INSERT INTO capacity_observation(id,component,environment,observed_at,request_rate,cpu_utilization,memory_utilization,p95_latency_ms,error_rate,active_replicas,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """,id,component,environment,observedAt,requestRate,cpu,memory,p95,errorRate,replicas,evidence);
        return id;
    }

    @Transactional
    public UUID forecast(String component,String environment,int horizonDays,OffsetDateTime windowStart,OffsetDateTime windowEnd,
                         BigDecimal safeRequestsPerReplica,String modelVersion){
        if(safeRequestsPerReplica==null||safeRequestsPerReplica.signum()<=0) throw new IllegalArgumentException("safeRequestsPerReplica must be positive");
        List<Observation> observations=jdbc.query("""
            SELECT observed_at,request_rate FROM capacity_observation
             WHERE component=? AND environment=? AND observed_at BETWEEN ? AND ? ORDER BY observed_at
            """,(rs,n)->new Observation(rs.getObject(1,OffsetDateTime.class),rs.getBigDecimal(2)),component,environment,windowStart,windowEnd);
        if(observations.size()<6) throw new IllegalStateException("at least six observations are required for forecast");
        BigDecimal forecastRate=linearForecast(observations,horizonDays*24d);
        BigDecimal lower=forecastRate.multiply(new BigDecimal("0.85")).max(BigDecimal.ZERO);
        BigDecimal upper=forecastRate.multiply(new BigDecimal("1.20"));
        int replicas=upper.divide(safeRequestsPerReplica,0,RoundingMode.CEILING).max(BigDecimal.ONE).intValueExact();
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(component,environment,horizonDays,forecastRate,replicas,windowStart,windowEnd,modelVersion);
        jdbc.update("""
            INSERT INTO capacity_forecast(id,component,environment,horizon_days,forecast_for,forecast_request_rate,required_replicas,confidence_lower,confidence_upper,model_version,source_window_start,source_window_end,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,component,environment,horizonDays,OffsetDateTime.now().plusDays(horizonDays),forecastRate,replicas,lower,upper,modelVersion,windowStart,windowEnd,evidence);
        return id;
    }


    @Transactional
    public UUID createPolicy(String component,String environment,int version,int minReplicas,int maxReplicas,
                             Integer targetCpuPercent,Integer targetMemoryPercent,int scaleUpPercent,
                             int scaleDownPercent,int stabilizationSeconds,String requester){
        if(component==null||component.isBlank()||environment==null||environment.isBlank()) throw new IllegalArgumentException("component and environment are required");
        if(minReplicas<=0||maxReplicas<minReplicas) throw new IllegalArgumentException("invalid replica bounds");
        UUID id=UUID.randomUUID();
        String evidence=ControlEvidence.sha256(component,environment,version,minReplicas,maxReplicas,targetCpuPercent,targetMemoryPercent,scaleUpPercent,scaleDownPercent,stabilizationSeconds,requester);
        jdbc.update("""
            INSERT INTO governed_autoscaling_policy(id,component,environment,version,min_replicas,max_replicas,target_cpu_percent,target_memory_percent,scale_up_percent,scale_down_percent,stabilization_seconds,status,requested_by,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)
            """,id,component,environment,version,minReplicas,maxReplicas,targetCpuPercent,targetMemoryPercent,scaleUpPercent,scaleDownPercent,stabilizationSeconds,requester,evidence);
        return id;
    }

    @Transactional
    public UUID requestPolicyActivation(UUID policyId,UUID forecastId,String reason,String rollbackReference,String requester){
        if(reason==null||reason.isBlank()||rollbackReference==null||rollbackReference.isBlank()) throw new IllegalArgumentException("reason and rollbackReference are required");
        Map<String,Object> policy=jdbc.queryForMap("SELECT id,requested_by,status FROM governed_autoscaling_policy WHERE id=? FOR UPDATE",policyId);
        if(!"DRAFT".equals(policy.get("status"))) throw new IllegalStateException("only a draft policy can be requested for activation");
        if(!requester.equals(policy.get("requested_by"))) throw new IllegalArgumentException("only the policy requester can submit activation");
        if(forecastId!=null){
            Integer count=jdbc.queryForObject("SELECT count(*) FROM capacity_forecast WHERE id=?",Integer.class,forecastId);
            if(count==null||count!=1) throw new IllegalArgumentException("forecast does not exist");
        }
        UUID id=UUID.randomUUID();
        String evidence=ControlEvidence.sha256(policyId,forecastId,reason,requester,rollbackReference);
        jdbc.update("""
            INSERT INTO capacity_change_request(id,policy_id,forecast_id,reason,requested_by,status,rollback_reference,evidence_hash)
            VALUES (?,?,?,?,?,'REQUESTED',?,?)
            """,id,policyId,forecastId,reason,requester,rollbackReference,evidence);
        return id;
    }

    @Transactional
    public void approveScalingChange(UUID requestId,String role,String actor){
        if(!role.equals("OPERATIONS")&&!role.equals("PERFORMANCE")) throw new IllegalArgumentException("role must be OPERATIONS or PERFORMANCE");
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM capacity_change_request WHERE id=? FOR UPDATE",requestId);
        if(!String.valueOf(row.get("status")).matches("REQUESTED|PARTIALLY_APPROVED")) throw new IllegalStateException("capacity request is not approvable");
        if(actor.equals(row.get("requested_by"))||actor.equals(row.get("operations_approved_by"))||actor.equals(row.get("performance_approved_by"))) throw new IllegalArgumentException("approval actors must be independent");
        jdbc.update("UPDATE capacity_change_request SET "+(role.equals("OPERATIONS")?"operations_approved_by":"performance_approved_by")+"=? WHERE id=?",actor,requestId);
        jdbc.update("UPDATE capacity_change_request SET status=CASE WHEN operations_approved_by IS NOT NULL AND performance_approved_by IS NOT NULL THEN 'APPROVED' ELSE 'PARTIALLY_APPROVED' END WHERE id=?",requestId);
    }


    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void applyApprovedPolicy(UUID requestId,String executor){
        Map<String,Object> request=jdbc.queryForMap("SELECT * FROM capacity_change_request WHERE id=? FOR UPDATE",requestId);
        if(!"APPROVED".equals(request.get("status"))) throw new IllegalStateException("capacity request is not fully approved");
        if(executor.equals(request.get("requested_by"))||executor.equals(request.get("operations_approved_by"))||executor.equals(request.get("performance_approved_by"))) throw new IllegalArgumentException("executor must be independent");
        UUID policyId=(UUID)request.get("policy_id");
        Map<String,Object> policy=jdbc.queryForMap("SELECT * FROM governed_autoscaling_policy WHERE id=? FOR UPDATE",policyId);
        if(!"DRAFT".equals(policy.get("status"))&&!"APPROVED".equals(policy.get("status"))) throw new IllegalStateException("policy is not activatable");
        String component=String.valueOf(policy.get("component")); String environment=String.valueOf(policy.get("environment"));
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Long.class,"autoscaling|"+component+"|"+environment);
        jdbc.update("UPDATE governed_autoscaling_policy SET status='RETIRED' WHERE component=? AND environment=? AND status='ACTIVE'",component,environment);
        int activated=jdbc.update("UPDATE governed_autoscaling_policy SET status='ACTIVE',approved_by=? WHERE id=? AND status IN ('DRAFT','APPROVED')",executor,policyId);
        if(activated!=1) throw new IllegalStateException("autoscaling policy activation failed");
        int applied=jdbc.update("UPDATE capacity_change_request SET status='APPLIED' WHERE id=? AND status='APPROVED'",requestId);
        if(applied!=1) throw new IllegalStateException("capacity request changed concurrently");
    }

    public static BigDecimal linearForecast(List<Observation> observations,double futureHours){
        int n=observations.size(); OffsetDateTime origin=observations.get(0).at();
        double sumX=0,sumY=0,sumXY=0,sumXX=0;
        for(Observation o:observations){double x=java.time.Duration.between(origin,o.at()).toMinutes()/60d;double y=o.rate().doubleValue();sumX+=x;sumY+=y;sumXY+=x*y;sumXX+=x*x;}
        double denominator=n*sumXX-sumX*sumX;
        double slope=Math.abs(denominator)<1e-9?0:(n*sumXY-sumX*sumY)/denominator;
        double intercept=(sumY-slope*sumX)/n;
        double lastX=java.time.Duration.between(origin,observations.get(n-1).at()).toMinutes()/60d;
        return BigDecimal.valueOf(Math.max(0,intercept+slope*(lastX+futureHours))).setScale(4,RoundingMode.HALF_UP);
    }
    public record Observation(OffsetDateTime at,BigDecimal rate){}
}
