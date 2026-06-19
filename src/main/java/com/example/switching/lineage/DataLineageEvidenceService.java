package com.example.switching.lineage;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DataLineageEvidenceService {
    private final JdbcTemplate jdbc;
    public DataLineageEvidenceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID registerAsset(String code,String type,String reference,String owner,String classification,boolean containsPii,String retentionPolicy){
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(code,type,reference,owner,classification,containsPii,retentionPolicy);
        jdbc.update("""
            INSERT INTO governed_data_asset(id,asset_code,asset_type,physical_reference,owner_team,classification,retention_policy_code,contains_pii,status,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?, 'ACTIVE',?)
            """,id,code,type,reference,owner,classification,retentionPolicy,containsPii,evidence);
        return id;
    }

    @Transactional
    public UUID addLineage(UUID source,UUID target,String transformation,String version,String purpose,String mappingHash,String approver){
        if(mappingHash!=null) ControlEvidence.requireSha256(mappingHash,"mappingHash");
        if(source.equals(target)) throw new IllegalArgumentException("lineage source and target must differ");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO data_lineage_edge(id,source_asset_id,target_asset_id,transformation_code,transformation_version,processing_purpose,field_mapping_hash,status,approved_by) VALUES (?,?,?,?,?,?,?,'ACTIVE',?)",
                id,source,target,transformation,version,purpose,mappingHash,approver);
        return id;
    }

    @Transactional
    public UUID registerEvidence(String controlCode,OffsetDateTime from,OffsetDateTime to,String artifactReference,
                                 String artifactHash,String contentType,long size,String producer){
        ControlEvidence.requireSha256(artifactHash,"artifactHash");
        if(artifactReference==null||artifactReference.contains("..")||artifactReference.startsWith("/")) throw new IllegalArgumentException("artifact reference must be a safe logical/object-store path");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO control_evidence_catalog(id,control_code,evidence_period_start,evidence_period_end,artifact_reference,artifact_sha256,content_type,size_bytes,producer,status)
            VALUES (?,?,?,?,?,?,?,?,?,'REGISTERED')
            """,id,controlCode,from,to,artifactReference,artifactHash,contentType,size,producer);
        return id;
    }

    @Transactional
    public boolean verifyEvidence(UUID evidenceId,String verifier,String observedHash,boolean accepted,String comment){
        ControlEvidence.requireSha256(observedHash,"observedHash");
        Map<String,Object> evidence=jdbc.queryForMap("SELECT artifact_sha256,status FROM control_evidence_catalog WHERE id=? FOR UPDATE",evidenceId);
        boolean hashMatches=observedHash.equals(evidence.get("artifact_sha256"));
        String decision=accepted&&hashMatches?"VERIFIED":"REJECTED";
        jdbc.update("INSERT INTO control_evidence_verification(id,evidence_id,verifier,decision,observed_sha256,comment) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),evidenceId,verifier,decision,observedHash,comment);
        jdbc.update("UPDATE control_evidence_catalog SET status=?,sealed_at=CASE WHEN ?='VERIFIED' THEN now() ELSE sealed_at END WHERE id=?",
                decision,decision,evidenceId);
        return "VERIFIED".equals(decision);
    }
}
