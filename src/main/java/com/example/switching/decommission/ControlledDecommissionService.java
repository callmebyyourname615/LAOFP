package com.example.switching.decommission;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ControlledDecommissionService {
    private final JdbcTemplate jdbc;
    public ControlledDecommissionService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID createPlan(String reference,String targetType,String targetCode,OffsetDateTime effectiveAt,String reason,
                           boolean dataExitRequired,String rollbackReference,String requester,List<TaskInput> tasks){
        if(tasks==null||tasks.isEmpty()) throw new IllegalArgumentException("decommission plan requires tasks");
        if(reference==null||reference.isBlank()||targetCode==null||targetCode.isBlank()||reason==null||reason.isBlank()||rollbackReference==null||rollbackReference.isBlank()) throw new IllegalArgumentException("reference, targetCode, reason and rollbackReference are required");
        if(!Set.of("PARTICIPANT","CONNECTOR","PRODUCT","SERVICE","DATASET").contains(targetType)) throw new IllegalArgumentException("invalid target type");
        if(effectiveAt==null||!effectiveAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("planned effective time must be in the future");
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(reference,targetType,targetCode,effectiveAt,reason,dataExitRequired,rollbackReference,requester,tasks);
        jdbc.update("""
            INSERT INTO decommission_plan(id,plan_reference,target_type,target_code,planned_effective_at,reason,data_exit_required,status,requested_by,rollback_reference,evidence_hash)
            VALUES (?,?,?,?,?,?,?,'DRAFT',?,?,?)
            """,id,reference,targetType,targetCode,effectiveAt,reason,dataExitRequired,requester,rollbackReference,evidence);
        int order=1;
        for(TaskInput task:tasks){
            jdbc.update("INSERT INTO decommission_task(id,plan_id,task_code,task_order,owner_team,description,blocking,status) VALUES (?,?,?,?,?,?,?,'PENDING')",
                    UUID.randomUUID(),id,task.code(),order++,task.ownerTeam(),task.description(),task.blocking());
        }
        event(id,"PLAN_CREATED",requester,"decommission plan created");
        return id;
    }

    @Transactional
    public void submit(UUID planId,String requester){
        int changed=jdbc.update("UPDATE decommission_plan SET status='SUBMITTED' WHERE id=? AND status='DRAFT' AND requested_by=?",planId,requester);
        if(changed!=1) throw new IllegalStateException("plan cannot be submitted");
        event(planId,"SUBMITTED",requester,"plan submitted for independent approvals");
    }

    @Transactional
    public void approve(UUID planId,String role,String actor){
        if(!Set.of("OPERATIONS","RISK","BUSINESS").contains(role)) throw new IllegalArgumentException("invalid approval role");
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM decommission_plan WHERE id=? FOR UPDATE",planId);
        if(!String.valueOf(row.get("status")).matches("SUBMITTED|APPROVED|BLOCKED")) throw new IllegalStateException("plan is not approvable");
        if(actor.equals(row.get("requested_by"))||actor.equals(row.get("operations_approved_by"))||actor.equals(row.get("risk_approved_by"))||actor.equals(row.get("business_approved_by"))) throw new IllegalArgumentException("approval actors must be independent");
        String column=switch(role){case "OPERATIONS"->"operations_approved_by";case "RISK"->"risk_approved_by";default->"business_approved_by";};
        jdbc.update("UPDATE decommission_plan SET "+column+"=? WHERE id=?",actor,planId);
        jdbc.update("""
            UPDATE decommission_plan SET status=CASE WHEN operations_approved_by IS NOT NULL AND risk_approved_by IS NOT NULL AND business_approved_by IS NOT NULL THEN 'APPROVED' ELSE status END WHERE id=?
            """,planId);
        event(planId,"APPROVAL_"+role,actor,"approval recorded");
    }

    @Transactional
    public void completeTask(UUID taskId,String actor,String evidenceHash,boolean waived){
        ControlEvidence.requireSha256(evidenceHash,"evidenceHash");
        Map<String,Object> task=jdbc.queryForMap("SELECT blocking,status FROM decommission_task WHERE id=? FOR UPDATE",taskId);
        if(waived&&Boolean.TRUE.equals(task.get("blocking"))) throw new IllegalArgumentException("blocking decommission tasks cannot be waived");
        int changed=jdbc.update("""
            UPDATE decommission_task SET status=?,completion_evidence_hash=?,completed_by=?,completed_at=now()
             WHERE id=? AND status IN ('PENDING','READY','RUNNING','FAILED')
            """,waived?"WAIVED":"DONE",evidenceHash,actor,taskId);
        if(changed!=1) throw new IllegalStateException("task cannot be completed in current state");
    }

    @Transactional
    public UUID addDataExitArtifact(UUID planId,String type,String reference,String hash,boolean encrypted,String recipient,long size,java.time.LocalDate retentionUntil,String actor){
        ControlEvidence.requireSha256(hash,"artifactHash");
        if(reference==null||reference.contains("..")||reference.startsWith("/")) throw new IllegalArgumentException("unsafe artifact reference");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO decommission_data_exit_artifact(id,plan_id,artifact_type,artifact_reference,artifact_sha256,encrypted,recipient_reference,size_bytes,retention_until,created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,id,planId,type,reference,hash,encrypted,recipient,size,retentionUntil,actor);
        return id;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void execute(UUID planId,String executor){
        Map<String,Object> plan=jdbc.queryForMap("SELECT * FROM decommission_plan WHERE id=? FOR UPDATE",planId);
        if(!"APPROVED".equals(plan.get("status"))) throw new IllegalStateException("plan is not fully approved");
        if(toOffsetDateTime(plan.get("planned_effective_at")).isAfter(OffsetDateTime.now())) throw new IllegalStateException("decommission cannot execute before planned effective time");
        if(executor.equals(plan.get("requested_by"))||executor.equals(plan.get("operations_approved_by"))||executor.equals(plan.get("risk_approved_by"))||executor.equals(plan.get("business_approved_by"))) throw new IllegalArgumentException("executor must be independent");
        Integer blocking=jdbc.queryForObject("SELECT count(*) FROM decommission_task WHERE plan_id=? AND blocking=true AND status <> 'DONE'",Integer.class,planId);
        if(blocking!=null&&blocking>0) throw new IllegalStateException("blocking decommission tasks are incomplete");
        if(Boolean.TRUE.equals(plan.get("data_exit_required"))){
            Integer artifacts=jdbc.queryForObject("SELECT count(*) FROM decommission_data_exit_artifact WHERE plan_id=? AND encrypted=true",Integer.class,planId);
            if(artifacts==null||artifacts==0) throw new IllegalStateException("encrypted data-exit evidence is required");
        }
        int executing=jdbc.update("UPDATE decommission_plan SET status='EXECUTING' WHERE id=? AND status='APPROVED'",planId);
        if(executing!=1) throw new IllegalStateException("decommission plan changed concurrently");
        String type=String.valueOf(plan.get("target_type")); String code=String.valueOf(plan.get("target_code"));
        if("PARTICIPANT".equals(type)){
            Integer unsettled=jdbc.queryForObject("SELECT count(*) FROM transactions WHERE (source_bank=? OR destination_bank=?) AND status NOT IN ('SETTLED','REJECTED','REFUNDED')",Integer.class,code,code);
            Integer disputes=jdbc.queryForObject("SELECT count(*) FROM disputes WHERE (raising_psp_id=? OR responding_psp_id=?) AND status NOT IN ('CLOSED','RESOLVED_REFUND','RESOLVED_NO_ACTION')",Integer.class,code,code);
            if((unsettled!=null&&unsettled>0)||(disputes!=null&&disputes>0)) throw new IllegalStateException("participant has unsettled transactions or open disputes");
            jdbc.update("UPDATE connector_configs SET enabled=false,force_reject=true,reject_reason_code='DECOMMISSIONED',reject_reason_message='Participant decommissioned by governed plan' WHERE bank_code=?",code);
            jdbc.update("UPDATE participant_product_entitlement SET status='REVOKED' WHERE participant_code=? AND status IN ('DRAFT','ACTIVE','SUSPENDED')",code);
            jdbc.update("UPDATE participants SET status='INACTIVE',updated_at=now() WHERE bank_code=?",code);
        } else if("CONNECTOR".equals(type)) {
            Integer unsettled=jdbc.queryForObject("SELECT count(*) FROM transactions WHERE connector_name=? AND status NOT IN ('SETTLED','REJECTED','REFUNDED')",Integer.class,code);
            if(unsettled!=null&&unsettled>0) throw new IllegalStateException("connector has unsettled transactions");
            jdbc.update("UPDATE connector_configs SET enabled=false,force_reject=true,reject_reason_code='DECOMMISSIONED',reject_reason_message='Connector decommissioned by governed plan' WHERE connector_name=?",code);
        } else if("PRODUCT".equals(type)) {
            jdbc.update("UPDATE participant_product_entitlement SET status='REVOKED' WHERE product_code=? AND status IN ('DRAFT','ACTIVE','SUSPENDED')",code);
            jdbc.update("UPDATE transaction_limit_policy SET status='RETIRED',effective_until=COALESCE(effective_until,now()) WHERE product_code=? AND status IN ('DRAFT','APPROVED','ACTIVE')",code);
        } else if("DATASET".equals(type)) {
            int retired=jdbc.update("UPDATE governed_data_asset SET status='RETIRED' WHERE asset_code=? AND status<>'RETIRED'",code);
            if(retired!=1) throw new IllegalStateException("governed dataset was not found or already retired");
        }
        jdbc.update("UPDATE decommission_plan SET status='COMPLETED',completed_at=now() WHERE id=?",planId);
        event(planId,"COMPLETED",executor,"controlled decommission completed for "+type+":"+code);
    }

    private static OffsetDateTime toOffsetDateTime(Object value){
        if(value instanceof OffsetDateTime time) return time;
        if(value instanceof java.sql.Timestamp time) return time.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private void event(UUID planId,String type,String actor,String detail){
        jdbc.update("INSERT INTO decommission_execution_event(id,plan_id,event_type,actor,detail,evidence_hash) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),planId,type,actor,detail,ControlEvidence.sha256(planId,type,actor,detail));
    }
    public record TaskInput(String code,String ownerTeam,String description,boolean blocking){}
}
