package com.example.switching.ledger;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialControlLedgerService {
    private final JdbcTemplate jdbc;

    public FinancialControlLedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UUID post(String sourceType, String sourceReference, LocalDate businessDate,
                     String currency, String actor, List<LedgerLine> lines) {
        requireText(sourceType, "sourceType"); requireText(sourceReference, "sourceReference");
        requireText(actor, "actor");
        if (businessDate == null) throw new IllegalArgumentException("businessDate is required");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be ISO-4217 uppercase");
        validateBalanced(lines);
        UUID journalId = UUID.randomUUID();
        String evidenceHash = evidenceHash(sourceType, sourceReference, businessDate, currency, lines);
        try {
            jdbc.update("""
                INSERT INTO control_journal(id, source_type, source_reference, business_date, currency, status, evidence_hash, created_by)
                VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """, journalId, sourceType, sourceReference, businessDate, currency, evidenceHash, actor);
            for (int i=0; i<lines.size(); i++) {
                LedgerLine line=lines.get(i);
                jdbc.update("""
                    INSERT INTO control_journal_entry(id,journal_id,line_no,account_code,side,amount,narrative)
                    VALUES (?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), journalId, i+1, line.accountCode(), line.side().name(), line.amount(), line.narrative());
            }
            jdbc.update("UPDATE control_journal SET status='POSTED' WHERE id=? AND status='OPEN'", journalId);
            return journalId;
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject("SELECT id FROM control_journal WHERE source_type=? AND source_reference=? AND currency=?",
                    UUID.class, sourceType, sourceReference, currency);
        }
    }

    public static void validateBalanced(List<LedgerLine> lines) {
        if (lines == null || lines.size() < 2) throw new IllegalArgumentException("at least two ledger lines are required");
        BigDecimal debit=BigDecimal.ZERO, credit=BigDecimal.ZERO;
        for (LedgerLine line: lines) {
            if (line.side()== LedgerLine.Side.DEBIT) debit=debit.add(line.amount()); else credit=credit.add(line.amount());
        }
        if (debit.compareTo(credit)!=0) throw new IllegalArgumentException("debits and credits must balance");
    }

    private static String evidenceHash(String sourceType, String reference, LocalDate date, String currency, List<LedgerLine> lines) {
        StringBuilder canonical=new StringBuilder(sourceType).append('|').append(reference).append('|').append(date).append('|').append(currency);
        for (int i=0;i<lines.size();i++) {
            LedgerLine line=lines.get(i);
            canonical.append('|').append(i+1).append(':').append(line.accountCode()).append(':').append(line.side()).append(':').append(line.amount().toPlainString());
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Unable to hash ledger evidence", e); }
    }
    private static void requireText(String value,String name){ if(value==null||value.isBlank()) throw new IllegalArgumentException(name+" is required"); }
}
