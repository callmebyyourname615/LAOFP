package com.example.switching.adjustments;

import com.example.switching.governance.ControlEvidence;
import com.example.switching.ledger.FinancialControlLedgerService;
import com.example.switching.ledger.LedgerLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualFinancialAdjustmentService {
    private final JdbcTemplate jdbc;
    private final FinancialControlLedgerService ledger;

    public ManualFinancialAdjustmentService(JdbcTemplate jdbc, FinancialControlLedgerService ledger) {
        this.jdbc=jdbc; this.ledger=ledger;
    }

    @Transactional
    public UUID create(String reference, LocalDate businessDate, String currency, String reasonCode,
                       String reasonDetail, String requester, List<LedgerLine> lines) {
        FinancialControlLedgerService.validateBalanced(lines);
        UUID id=UUID.randomUUID();
        String evidence=ControlEvidence.sha256(reference,businessDate,currency,reasonCode,reasonDetail,requester,lines);
        jdbc.update("""
            INSERT INTO manual_financial_adjustment(id,adjustment_reference,business_date,currency,reason_code,reason_detail,status,requested_by,evidence_hash)
            VALUES (?,?,?,?,?,?,'DRAFT',?,?)
            """,id,reference,businessDate,currency,reasonCode,reasonDetail,requester,evidence);
        for(int i=0;i<lines.size();i++){
            LedgerLine line=lines.get(i);
            jdbc.update("INSERT INTO manual_financial_adjustment_line(id,adjustment_id,line_no,account_code,side,amount,narrative) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),id,i+1,line.accountCode(),line.side().name(),line.amount(),line.narrative());
        }
        return id;
    }

    @Transactional
    public void submit(UUID id,String actor,String comment){
        int changed=jdbc.update("UPDATE manual_financial_adjustment SET status='SUBMITTED',submitted_at=now() WHERE id=? AND status='DRAFT' AND requested_by=?",id,actor);
        if(changed!=1) throw new IllegalStateException("only requester can submit a draft adjustment");
        event(id,actor,"SUBMITTED",comment);
    }

    @Transactional
    public void decide(UUID id,String approver,boolean approved,String comment){
        int changed=jdbc.update("""
            UPDATE manual_financial_adjustment SET status=?,approved_by=?,approved_at=CASE WHEN ? THEN now() ELSE approved_at END
             WHERE id=? AND status='SUBMITTED' AND requested_by<>?
            """,approved?"APPROVED":"REJECTED",approver,approved,id,approver);
        if(changed!=1) throw new IllegalStateException("adjustment cannot be decided by requester or in current state");
        event(id,approver,approved?"APPROVED":"REJECTED",comment);
    }

    @Transactional
    public UUID execute(UUID id,String executor){
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM manual_financial_adjustment WHERE id=? FOR UPDATE",id);
        if(!"APPROVED".equals(row.get("status"))) throw new IllegalStateException("adjustment is not approved");
        if(executor.equals(row.get("requested_by"))||executor.equals(row.get("approved_by"))) throw new IllegalArgumentException("executor must be independent");
        List<LedgerLine> lines=jdbc.query("""
            SELECT account_code,side,amount,narrative FROM manual_financial_adjustment_line WHERE adjustment_id=? ORDER BY line_no
            """,(rs,n)->new LedgerLine(rs.getString(1),LedgerLine.Side.valueOf(rs.getString(2)),rs.getBigDecimal(3),rs.getString(4)),id);
        UUID journalId=ledger.post("MANUAL_ADJUSTMENT",String.valueOf(row.get("adjustment_reference")),
                toLocalDate(row.get("business_date")),String.valueOf(row.get("currency")),executor,lines);
        int changed=jdbc.update("UPDATE manual_financial_adjustment SET status='EXECUTED',executed_by=?,execution_journal_id=?,executed_at=now() WHERE id=? AND status='APPROVED'",
                executor,journalId,id);
        if(changed!=1) throw new IllegalStateException("adjustment execution race detected");
        event(id,executor,"EXECUTED","posted journal "+journalId);
        return journalId;
    }

    private static LocalDate toLocalDate(Object value){
        if(value instanceof LocalDate date) return date;
        if(value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private void event(UUID id,String actor,String decision,String comment){
        String evidence=ControlEvidence.sha256(id,actor,decision,comment);
        jdbc.update("INSERT INTO manual_adjustment_approval_event(id,adjustment_id,actor,decision,comment,evidence_hash) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),id,actor,decision,comment,evidence);
    }
}
