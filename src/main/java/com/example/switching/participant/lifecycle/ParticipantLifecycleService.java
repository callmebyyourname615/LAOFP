package com.example.switching.participant.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ParticipantLifecycleService {
    private final JdbcTemplate jdbcTemplate;

    public ParticipantLifecycleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ParticipantLifecycleDecision approve(UUID caseId, String approver, Instant effectiveAt) {
        int updated = jdbcTemplate.update("""
                UPDATE participant_lifecycle_case
                SET status = 'APPROVED', approved_by = ?, approved_at = now(), effective_at = ?
                WHERE id = ? AND status IN ('DRAFT','SUBMITTED') AND requested_by <> ?
                """, approver, OffsetDateTime.from(effectiveAt.atOffset(java.time.ZoneOffset.UTC)), caseId, approver);
        if (updated != 1) {
            throw new IllegalStateException("Lifecycle case cannot be approved by same requester or is not actionable");
        }
        return jdbcTemplate.queryForObject("""
                SELECT id, participant_code, status, effective_at FROM participant_lifecycle_case WHERE id = ?
                """, (rs, rowNum) -> new ParticipantLifecycleDecision(
                UUID.fromString(rs.getString("id")),
                rs.getString("participant_code"),
                rs.getString("status"),
                rs.getTimestamp("effective_at").toInstant()), caseId);
    }
}
