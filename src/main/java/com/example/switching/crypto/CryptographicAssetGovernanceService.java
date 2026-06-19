package com.example.switching.crypto;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class CryptographicAssetGovernanceService {
    private final JdbcTemplate jdbc;
    public CryptographicAssetGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID register(String code,String type,String provider,String externalReference,String fingerprint,String algorithm,
                         Integer keySize,String owner,String environment,int rotationDays,OffsetDateTime nextRotation,String actor){
        if(externalReference==null||externalReference.matches("(?is).*(BEGIN .*PRIVATE KEY|secret=|password=).*")) throw new IllegalArgumentException("secret material must never be stored in crypto inventory");
        if(fingerprint!=null) ControlEvidence.requireSha256(fingerprint,"fingerprint");
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(code,type,provider,externalReference,fingerprint,algorithm,keySize,owner,environment,rotationDays,nextRotation,actor);
        jdbc.update("""
            INSERT INTO cryptographic_asset_inventory(id,asset_code,asset_type,provider,external_reference,fingerprint_sha256,algorithm,key_size_bits,owner_team,environment,status,rotation_interval_days,next_rotation_at,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,'PLANNED',?,?,?)
            """,id,code,type,provider,externalReference,fingerprint,algorithm,keySize,owner,environment,rotationDays,nextRotation,evidence);
        return id;
    }

    @Transactional
    public UUID scheduleRotation(UUID assetId,OffsetDateTime plannedFor,OffsetDateTime overlapUntil,String rollbackReference,String actor){
        UUID id=UUID.randomUUID(); String evidence=ControlEvidence.sha256(assetId,plannedFor,overlapUntil,rollbackReference,actor);
        int created=jdbc.update("""
            INSERT INTO cryptographic_rotation_plan(id,asset_id,planned_for,overlap_until,rollback_reference,requested_by,status,evidence_hash)
            SELECT ?,?,?,?,?,?,'REQUESTED',? FROM cryptographic_asset_inventory WHERE id=? AND status IN ('ACTIVE','ROTATING')
            """,id,assetId,plannedFor,overlapUntil,rollbackReference,actor,evidence,assetId);
        if(created!=1) throw new IllegalStateException("cryptographic asset is not active/rotating");
        return id;
    }

    @Transactional
    public void approveRotation(UUID planId,String approver){
        int changed=jdbc.update("""
            UPDATE cryptographic_rotation_plan SET status='APPROVED',approved_by=?
             WHERE id=? AND status='REQUESTED' AND requested_by<>? AND planned_for>now()
            """,approver,planId,approver);
        if(changed!=1) throw new IllegalStateException("rotation plan cannot be approved");
    }

    @Transactional
    public void completeRotation(UUID planId,String executor,String newFingerprint,String artifactReference,String artifactHash){
        ControlEvidence.requireSha256(newFingerprint,"newFingerprint"); ControlEvidence.requireSha256(artifactHash,"artifactHash");
        Map<String,Object> plan=jdbc.queryForMap("SELECT * FROM cryptographic_rotation_plan WHERE id=? FOR UPDATE",planId);
        if(!"APPROVED".equals(plan.get("status"))) throw new IllegalStateException("rotation plan is not approved");
        if(executor.equals(plan.get("requested_by"))) throw new IllegalArgumentException("requester cannot execute rotation");
        UUID assetId=(UUID)plan.get("asset_id");
        String old=jdbc.queryForObject("SELECT fingerprint_sha256 FROM cryptographic_asset_inventory WHERE id=? FOR UPDATE",String.class,assetId);
        jdbc.update("""
            UPDATE cryptographic_asset_inventory SET fingerprint_sha256=?,status='ACTIVE',last_rotated_at=now(),
                   next_rotation_at=now()+(rotation_interval_days * interval '1 day') WHERE id=?
            """,newFingerprint,assetId);
        jdbc.update("UPDATE cryptographic_rotation_plan SET status='COMPLETED',executed_by=?,old_fingerprint=?,new_fingerprint=?,completed_at=now() WHERE id=?",
                executor,old,newFingerprint,planId);
        jdbc.update("INSERT INTO cryptographic_rotation_evidence(id,rotation_plan_id,evidence_type,artifact_reference,artifact_sha256,recorded_by) VALUES (?,?, 'ROTATION_RESULT',?,?,?)",
                UUID.randomUUID(),planId,artifactReference,artifactHash,executor);
    }
}
