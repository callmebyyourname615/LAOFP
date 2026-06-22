package com.example.switching.rtp.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.rtp.entity.RtpRequestEntity;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.repository.RtpRequestRepository;

@Component
@Profile("!migration")
@ConditionalOnProperty(prefix = "switching.phase-ii.rtp", name = "enabled", havingValue = "true")
public class RtpExpiryScheduler {
    private final JdbcTemplate jdbc;
    private final RtpRequestRepository requests;
    private final RtpDomainEventPublisher events;
    private final Clock clock;

    public RtpExpiryScheduler(JdbcTemplate jdbc, RtpRequestRepository requests,
            RtpDomainEventPublisher events, @Qualifier("rtpClock") Clock clock) {
        this.jdbc = jdbc; this.requests = requests; this.events = events; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${switching.phase-ii.rtp.expiry-scan-ms:60000}")
    @Transactional
    public void expire() {
        var ids = jdbc.query("""
                SELECT id FROM rtp_request
                 WHERE status='PENDING_AUTH' AND expires_at<=?
                 ORDER BY expires_at LIMIT 500 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        for (UUID id : ids) {
            int changed = jdbc.update("""
                    UPDATE rtp_request SET status='EXPIRED',updated_at=now(),version=version+1
                     WHERE id=? AND status='PENDING_AUTH'
                    """, id);
            if (changed == 1) {
                jdbc.update("""
                        INSERT INTO rtp_state_transition(id,request_id,from_status,to_status,actor_id,reason)
                        VALUES (?,?,'PENDING_AUTH','EXPIRED','system','RTP expiry policy')
                        """, UUID.randomUUID(), id);
                RtpRequestEntity request = requests.findById(id).orElseThrow();
                request.setStatus(RtpStatus.EXPIRED);
                events.publish("rtp.expired", request, Map.of("reason", "RTP expiry policy"));
            }
        }
    }
}
