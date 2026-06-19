package com.example.switching.settlement.evidence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class SettlementEvidenceService {
    private final JdbcTemplate jdbcTemplate;

    public SettlementEvidenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String appendEvidence(String cycleId, String evidenceType, String participantCode, String sourceUri, String sourceSha256, String createdBy) {
        String previousHash = jdbcTemplate.queryForObject("""
                SELECT chain_hash FROM settlement_evidence_ledger
                WHERE settlement_cycle_id = ? ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), cycleId);
        String chainHash = sha256((previousHash == null ? "GENESIS" : previousHash) + cycleId + evidenceType + sourceSha256);
        jdbcTemplate.update("""
                INSERT INTO settlement_evidence_ledger(settlement_cycle_id, evidence_type, participant_code, source_uri, source_sha256, previous_hash, chain_hash, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, cycleId, evidenceType, participantCode, sourceUri, sourceSha256, previousHash, chainHash, createdBy);
        return chainHash;
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
