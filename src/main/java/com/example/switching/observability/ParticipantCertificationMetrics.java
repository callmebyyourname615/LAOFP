package com.example.switching.observability;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Profile("!migration")
public class ParticipantCertificationMetrics {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong activeWithoutCurrentPass = new AtomicLong();
    private final AtomicLong currentPassExpiringThirtyDays = new AtomicLong();

    public ParticipantCertificationMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("switching.participants.active.without.current.certification", activeWithoutCurrentPass,
                AtomicLong::get).register(registry);
        Gauge.builder("switching.participants.current.certification.expiring.thirty.days",
                currentPassExpiringThirtyDays, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${switching.observability.participant-certification-refresh:PT5M}")
    public void refresh() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM participants p
                LEFT JOIN LATERAL (
                  SELECT c.result, c.expires_at
                  FROM participant_certifications c
                  WHERE c.bank_code = p.bank_code
                  ORDER BY c.executed_at DESC, c.id DESC
                  LIMIT 1
                ) latest ON TRUE
                WHERE p.status = 'ACTIVE'
                  AND (latest.result IS DISTINCT FROM 'PASS' OR latest.expires_at <= NOW())
                """, Long.class);
        activeWithoutCurrentPass.set(count == null ? 0L : count);
        Long expiring = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM participants p
                JOIN LATERAL (
                  SELECT c.result, c.expires_at
                  FROM participant_certifications c
                  WHERE c.bank_code = p.bank_code
                  ORDER BY c.executed_at DESC, c.id DESC
                  LIMIT 1
                ) latest ON TRUE
                WHERE p.status = 'ACTIVE'
                  AND latest.result = 'PASS'
                  AND latest.expires_at > NOW()
                  AND latest.expires_at <= NOW() + interval '30 days'
                """, Long.class);
        currentPassExpiringThirtyDays.set(expiring == null ? 0L : expiring);
    }
}
