package com.example.switching.certificates;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class ParticipantCertificateLifecycleService {
    private final JdbcTemplate jdbc;
    public ParticipantCertificateLifecycleService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID register(String participant,String type,byte[] derCertificate,String requester,UUID replaces){
        if(participant==null||participant.isBlank()||requester==null||requester.isBlank()) throw new IllegalArgumentException("participant and requester are required");
        if(!java.util.Set.of("CLIENT_MTLS","MESSAGE_SIGNING","WEBHOOK_SIGNING").contains(type)) throw new IllegalArgumentException("unsupported certificate type");
        if(derCertificate==null||derCertificate.length==0||derCertificate.length>65536) throw new IllegalArgumentException("certificate size is invalid");
        try{
            X509Certificate cert=(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(derCertificate));
            cert.checkValidity();
            Instant notBefore=cert.getNotBefore().toInstant(), notAfter=cert.getNotAfter().toInstant();
            if(notAfter.isAfter(notBefore.plus(825,ChronoUnit.DAYS))) throw new IllegalArgumentException("certificate lifetime exceeds policy");
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            UUID id=UUID.randomUUID(); String evidence=hash(participant+"|"+type+"|"+fingerprint+"|"+notBefore+"|"+notAfter);
            jdbc.update("""
                INSERT INTO participant_certificate(id,participant_code,certificate_type,fingerprint_sha256,serial_number,subject_dn,issuer_dn,not_before,not_after,status,requested_by,replaced_certificate_id,evidence_hash)
                VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?,?)
                """,id,participant,type,fingerprint,cert.getSerialNumber().toString(16),cert.getSubjectX500Principal().getName(),cert.getIssuerX500Principal().getName(),
                    cert.getNotBefore(),cert.getNotAfter(),requester,replaces,evidence);
            recordEvent(id,"REGISTERED",requester,"certificate metadata registered",evidence);
            return id;
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("invalid X.509 certificate",e);}
    }

    @Transactional
    public void activate(UUID id,String approver){
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM participant_certificate WHERE id=? FOR UPDATE",id);
        if(approver.equals(row.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve certificate");
        if(!"PENDING".equals(row.get("status"))) throw new IllegalStateException("certificate is not pending");
        Boolean sufficientlyValid=jdbc.queryForObject("SELECT not_after > now()+interval '14 days' FROM participant_certificate WHERE id=?",Boolean.class,id);
        if(!Boolean.TRUE.equals(sufficientlyValid)) throw new IllegalStateException("certificate has insufficient remaining validity");
        jdbc.update("UPDATE participant_certificate SET status='OVERLAP' WHERE participant_code=? AND certificate_type=? AND status='ACTIVE'",
                row.get("participant_code"),row.get("certificate_type"));
        jdbc.update("UPDATE participant_certificate SET status='ACTIVE',approved_by=?,activated_at=now() WHERE id=?",approver,id);
        recordEvent(id,"ACTIVATED",approver,"four-eyes activation",String.valueOf(row.get("evidence_hash")));
    }

    @Transactional
    public void revoke(UUID id,String actor,String reason){
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("revocation reason is required");
        int changed=jdbc.update("UPDATE participant_certificate SET status='REVOKED',revoked_at=now(),revocation_reason=? WHERE id=? AND status IN ('ACTIVE','OVERLAP','PENDING')",reason,id);
        if(changed!=1) throw new IllegalStateException("certificate cannot be revoked from current status");
        recordEvent(id,"REVOKED",actor,reason,hash(id+"|"+actor+"|"+reason));
    }
    private void recordEvent(UUID id,String type,String actor,String reason,String evidence){jdbc.update("INSERT INTO certificate_lifecycle_event(certificate_id,event_type,actor,reason,evidence_hash) VALUES (?,?,?,?,?)",id,type,actor,reason,evidence);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
