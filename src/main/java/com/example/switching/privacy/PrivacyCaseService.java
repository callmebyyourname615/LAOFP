package com.example.switching.privacy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PrivacyCaseService {
    private final JdbcTemplate jdbcTemplate;

    public PrivacyCaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String openCase(String subjectReference, String caseType, String legalBasis, int daysToDue) {
        String caseReference = "privacy-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO privacy_access_case(case_reference, requester_type, subject_reference, case_type, legal_basis, due_at)
                VALUES (?, 'PARTICIPANT', ?, ?, ?, ?)
                """, caseReference, subjectReference, caseType, legalBasis,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysToDue));
        return caseReference;
    }
}
