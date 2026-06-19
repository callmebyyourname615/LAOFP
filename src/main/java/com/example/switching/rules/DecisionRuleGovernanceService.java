package com.example.switching.rules;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DecisionRuleGovernanceService {
    private final JdbcTemplate jdbc;
    public DecisionRuleGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID createVersion(String packageCode,String version,String artifactReference,String artifactHash,
                              String manifestHash,String reason,String requester){
        ControlEvidence.requireSha256(artifactHash,"artifactHash"); ControlEvidence.requireSha256(manifestHash,"manifestHash");
        UUID packageId=jdbc.queryForObject("SELECT id FROM decision_rule_package WHERE package_code=? AND status='ACTIVE'",UUID.class,packageCode);
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(packageCode,version,artifactReference,artifactHash,manifestHash,reason,requester);
        jdbc.update("""
            INSERT INTO decision_rule_version(id,package_id,version,artifact_reference,artifact_sha256,manifest_sha256,change_reason,requested_by,status,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,'DRAFT',?)
            """,id,packageId,version,artifactReference,artifactHash,manifestHash,reason,requester,evidence);
        return id;
    }

    @Transactional
    public void recordTest(UUID versionId,String suiteVersion,int total,int passed,int failed,BigDecimal falsePositive,
                           BigDecimal falseNegative,String status,String resultReference,String resultHash,String executor){
        if(total<=0||passed<0||failed<0||passed+failed!=total) throw new IllegalArgumentException("invalid test counts");
        if(("PASS".equals(status)&&failed!=0)||(!"PASS".equals(status)&&!"FAIL".equals(status))) throw new IllegalArgumentException("invalid test status");
        ControlEvidence.requireSha256(resultHash,"resultHash");
        jdbc.update("""
            INSERT INTO decision_rule_test_execution(id,rule_version_id,suite_version,test_case_count,passed_count,failed_count,false_positive_rate,false_negative_rate,status,result_artifact_reference,result_sha256,executed_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),versionId,suiteVersion,total,passed,failed,falsePositive,falseNegative,status,resultReference,resultHash,executor);
        jdbc.update("UPDATE decision_rule_version SET status=? WHERE id=? AND status IN ('DRAFT','TESTING','READY')", "PASS".equals(status)?"READY":"TESTING",versionId);
    }

    @Transactional
    public void approve(UUID versionId,String role,String actor){
        if(!role.equals("RISK")&&!role.equals("COMPLIANCE")) throw new IllegalArgumentException("role must be RISK or COMPLIANCE");
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM decision_rule_version WHERE id=? FOR UPDATE",versionId);
        if(!String.valueOf(row.get("status")).matches("READY|APPROVED")) throw new IllegalStateException("rule version is not ready for approval");
        if(actor.equals(row.get("requested_by"))||actor.equals(row.get("risk_approved_by"))||actor.equals(row.get("compliance_approved_by"))) throw new IllegalArgumentException("approval actors must be independent");
        String column=role.equals("RISK")?"risk_approved_by":"compliance_approved_by";
        jdbc.update("UPDATE decision_rule_version SET "+column+"=? WHERE id=?",actor,versionId);
        jdbc.update("UPDATE decision_rule_version SET status=CASE WHEN risk_approved_by IS NOT NULL AND compliance_approved_by IS NOT NULL THEN 'APPROVED' ELSE status END WHERE id=?",versionId);
    }

    @Transactional
    public UUID activate(UUID versionId,String environment,String deploymentReference,String deployer,OffsetDateTime effectiveFrom,OffsetDateTime effectiveUntil){
        Map<String,Object> row=jdbc.queryForMap("SELECT package_id,status,requested_by,risk_approved_by,compliance_approved_by FROM decision_rule_version WHERE id=? FOR UPDATE",versionId);
        if(!"APPROVED".equals(row.get("status"))) throw new IllegalStateException("rule version is not fully approved");
        if(deployer.equals(row.get("requested_by"))||deployer.equals(row.get("risk_approved_by"))||deployer.equals(row.get("compliance_approved_by"))) throw new IllegalArgumentException("deployer must be independent");
        Integer passing=jdbc.queryForObject("SELECT count(*) FROM decision_rule_test_execution WHERE rule_version_id=? AND status='PASS'",Integer.class,versionId);
        if(passing==null||passing==0) throw new IllegalStateException("rule version has no passing test evidence");
        UUID packageId=(UUID)row.get("package_id");
        UUID previous=jdbc.query("SELECT id FROM decision_rule_version WHERE package_id=? AND status='ACTIVE'",rs->rs.next()?(UUID)rs.getObject(1):null,packageId);
        jdbc.update("UPDATE decision_rule_version SET status='RETIRED',effective_until=COALESCE(effective_until,now()) WHERE package_id=? AND status='ACTIVE'",packageId);
        jdbc.update("UPDATE decision_rule_version SET status='ACTIVE',effective_from=?,effective_until=? WHERE id=?",effectiveFrom,effectiveUntil,versionId);
        UUID deploymentId=UUID.randomUUID(); String evidence=ControlEvidence.sha256(versionId,environment,deploymentReference,previous,deployer,effectiveFrom,effectiveUntil);
        jdbc.update("""
            INSERT INTO decision_rule_deployment(id,rule_version_id,environment,deployment_reference,previous_version_id,deployed_by,status,completed_at,evidence_hash)
            VALUES (?,?,?,?,?,?,'ACTIVE',now(),?)
            """,deploymentId,versionId,environment,deploymentReference,previous,deployer,evidence);
        return deploymentId;
    }

    @Transactional
    public void rollback(UUID deploymentId,String actor){
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM decision_rule_deployment WHERE id=? FOR UPDATE",deploymentId);
        if(!"ACTIVE".equals(row.get("status"))) throw new IllegalStateException("only active deployment can be rolled back");
        UUID current=(UUID)row.get("rule_version_id"); UUID previous=(UUID)row.get("previous_version_id");
        jdbc.update("UPDATE decision_rule_version SET status='ROLLED_BACK',effective_until=now() WHERE id=?",current);
        if(previous!=null) jdbc.update("UPDATE decision_rule_version SET status='ACTIVE',effective_until=NULL WHERE id=?",previous);
        jdbc.update("UPDATE decision_rule_deployment SET status='ROLLED_BACK',completed_at=now(),deployed_by=? WHERE id=?",actor,deploymentId);
    }
}
