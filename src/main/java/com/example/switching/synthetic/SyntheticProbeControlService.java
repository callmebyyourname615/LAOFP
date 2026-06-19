package com.example.switching.synthetic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class SyntheticProbeControlService {
    private final JdbcTemplate jdbc;
    public SyntheticProbeControlService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID start(String probeCode,String reference){
        if(reference==null||!reference.matches("SYN-[A-Z0-9_-]{8,120}")) throw new IllegalArgumentException("synthetic reference must use reserved SYN- prefix");
        Map<String,Object> definition=jdbc.queryForMap("SELECT participant_code,enabled FROM synthetic_probe_definition WHERE probe_code=?",probeCode);
        if(!Boolean.TRUE.equals(definition.get("enabled"))||!String.valueOf(definition.get("participant_code")).startsWith("SYN")) throw new IllegalStateException("probe is not enabled for a synthetic participant");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO synthetic_probe_execution(id,probe_code,synthetic_reference,started_at,status) VALUES (?,?,?,now(),'RUNNING')",id,probeCode,reference);
        return id;
    }

    @Transactional
    public void complete(UUID id,String status,String responseCode,String cleanupStatus,String error){
        if(!java.util.Set.of("PASS","FAIL","TIMEOUT","CLEANUP_FAILED").contains(status)) throw new IllegalArgumentException("invalid probe status");
        Map<String,Object> row=jdbc.queryForMap("SELECT synthetic_reference,started_at,status FROM synthetic_probe_execution WHERE id=? FOR UPDATE",id);
        if(!"RUNNING".equals(row.get("status"))) return;
        Long measured=jdbc.queryForObject("SELECT greatest(0,extract(epoch from (now()-started_at))*1000)::bigint FROM synthetic_probe_execution WHERE id=?",Long.class,id);
        long latency=measured==null?0L:measured;
        String boundedError=error==null?null:error.substring(0,Math.min(500,error.length()));
        String evidence=hash(row.get("synthetic_reference")+"|"+status+"|"+responseCode+"|"+cleanupStatus+"|"+latency);
        jdbc.update("UPDATE synthetic_probe_execution SET completed_at=now(),status=?,latency_ms=?,response_code=?,cleanup_status=?,evidence_hash=?,error_summary=? WHERE id=?",
                status,latency,responseCode,cleanupStatus,evidence,boundedError,id);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
