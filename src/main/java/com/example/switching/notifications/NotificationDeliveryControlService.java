package com.example.switching.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDeliveryControlService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public NotificationDeliveryControlService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @Transactional
    public UUID queue(String dedupe,String templateCode,String channel,String locale,String recipientReference,Map<String,Object> payload){
        UUID version=jdbc.queryForObject("""
            SELECT id FROM notification_template_version WHERE template_code=? AND channel=? AND locale=? AND status='ACTIVE'
            """,UUID.class,templateCode,channel,locale);
        try{
            assertSafePayload(payload);
            String json=mapper.writeValueAsString(payload);
            if(json.getBytes(StandardCharsets.UTF_8).length>65536) throw new IllegalArgumentException("notification payload exceeds 64 KiB");
            String recipientHash=hash(recipientReference);
            String evidence=hash(dedupe+"|"+version+"|"+recipientHash+"|"+json);
            UUID id=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO notification_delivery(id,deduplication_key,template_version_id,recipient_reference_hash,payload_json,status,evidence_hash)
                VALUES (?,?,?,?,?::jsonb,'QUEUED',?) ON CONFLICT(deduplication_key) DO NOTHING
                """,id,dedupe,version,recipientHash,json,evidence);
            return jdbc.queryForObject("SELECT id FROM notification_delivery WHERE deduplication_key=?",UUID.class,dedupe);
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("notification payload cannot be serialized",e);}
    }

    @Transactional
    public void markAttempt(UUID id,boolean delivered,String providerReference,String errorCode,int maxAttempts){
        Map<String,Object> row=jdbc.queryForMap("SELECT attempt_count,status FROM notification_delivery WHERE id=? FOR UPDATE",id);
        if("DELIVERED".equals(row.get("status"))||"DEAD".equals(row.get("status"))) return;
        int attempts=((Number)row.get("attempt_count")).intValue()+1;
        if(delivered){
            jdbc.update("UPDATE notification_delivery SET status='DELIVERED',attempt_count=?,provider_reference=?,delivered_at=now(),last_error_code=NULL WHERE id=?",attempts,providerReference,id);
        }else{
            String status=attempts>=maxAttempts?"DEAD":"RETRY";
            Duration backoff=backoff(attempts);
            jdbc.update("UPDATE notification_delivery SET status=?,attempt_count=?,last_error_code=?,next_attempt_at=? WHERE id=?",status,attempts,errorCode,OffsetDateTime.now().plus(backoff),id);
        }
    }
    public static Duration backoff(int attempt){long seconds=Math.min(3600L,30L*(1L<<Math.min(7,Math.max(0,attempt-1))));return Duration.ofSeconds(seconds);}
    static void assertSafePayload(Object value){
        if(value instanceof Map<?,?> map){
            for(var entry:map.entrySet()){
                String key=String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT).replace("_","").replace("-","");
                if(java.util.Set.of("password","pin","cvv","privatekey","secret","accesstoken","refreshtoken").contains(key)) throw new IllegalArgumentException("secret-like notification payload field is forbidden");
                assertSafePayload(entry.getValue());
            }
        }else if(value instanceof Iterable<?> iterable){for(Object item:iterable) assertSafePayload(item);}
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
