package com.example.switching.regulatory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class RegulatoryReportControlService {
    private final JdbcTemplate jdbc;
    public RegulatoryReportControlService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID start(String reportCode,LocalDate start,LocalDate end,String generator){
        if(end.isBefore(start)) throw new IllegalArgumentException("period end precedes period start");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO regulatory_report_run(id,report_code,period_start,period_end,status,generated_by)
            VALUES (?,?,?,?, 'GENERATING', ?) ON CONFLICT(report_code,period_start,period_end) DO NOTHING
            """,id,reportCode,start,end,generator);
        return jdbc.queryForObject("SELECT id FROM regulatory_report_run WHERE report_code=? AND period_start=? AND period_end=?",UUID.class,reportCode,start,end);
    }

    @Transactional
    public void validate(UUID runId,String validator,long records,java.math.BigDecimal total,String objectKey,long size,String artifactSha){
        Map<String,Object> row=jdbc.queryForMap("SELECT generated_by,status FROM regulatory_report_run WHERE id=? FOR UPDATE",runId);
        if(validator.equals(row.get("generated_by"))) throw new IllegalArgumentException("generator cannot validate regulatory report");
        if(!"GENERATING".equals(row.get("status"))) throw new IllegalStateException("report run is not generating");
        if(records<0||size<=0||artifactSha==null||!artifactSha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid report evidence");
        if(objectKey==null||objectKey.isBlank()||objectKey.startsWith("/")||objectKey.contains("..")||objectKey.contains("\\")) throw new IllegalArgumentException("unsafe regulatory artifact key");
        String evidence=hash(runId+"|"+records+"|"+total+"|"+objectKey+"|"+size+"|"+artifactSha);
        jdbc.update("INSERT INTO regulatory_report_artifact(id,report_run_id,object_key,media_type,size_bytes,sha256) VALUES (?,?,?,'application/octet-stream',?,?)",UUID.randomUUID(),runId,objectKey,size,artifactSha);
        jdbc.update("UPDATE regulatory_report_run SET status='VALIDATED',validated_by=?,record_count=?,total_amount=?,evidence_hash=?,completed_at=now() WHERE id=?",validator,records,total,evidence,runId);
    }

    @Transactional
    public UUID submit(UUID runId,String actor,String submissionReference){
        Map<String,Object> row=jdbc.queryForMap("SELECT generated_by,validated_by,status FROM regulatory_report_run WHERE id=? FOR UPDATE",runId);
        if(!"VALIDATED".equals(row.get("status"))) throw new IllegalStateException("only validated reports may be submitted");
        if(actor.equals(row.get("generated_by"))||actor.equals(row.get("validated_by"))) throw new IllegalArgumentException("submitter must be independent");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO regulatory_report_submission(id,report_run_id,submission_reference,submitted_by,response_status) VALUES (?,?,?,?,'PENDING')",id,runId,submissionReference,actor);
        jdbc.update("UPDATE regulatory_report_run SET status='SUBMITTED' WHERE id=?",runId);
        return id;
    }

    @Transactional
    public void acknowledge(UUID submissionId,String code,byte[] response,boolean accepted){
        String responseHash=hashBytes(response);
        jdbc.update("UPDATE regulatory_report_submission SET acknowledgement_code=?,acknowledgement_hash=?,acknowledged_at=now(),response_status=? WHERE id=? AND response_status='PENDING'",
                code,responseHash,accepted?"ACCEPTED":"REJECTED",submissionId);
        jdbc.update("UPDATE regulatory_report_run SET status=? WHERE id=(SELECT report_run_id FROM regulatory_report_submission WHERE id=?)",accepted?"ACKNOWLEDGED":"REJECTED",submissionId);
    }
    private static String hash(String value){return hashBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static String hashBytes(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
