package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementOpsReportService {

    private final JdbcTemplate jdbc;

    public SettlementOpsReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public OpsSettlementReport load(String cycleRef) {
        Cycle cycle = loadCycle(cycleRef);
        if (!"SETTLED".equals(cycle.status())) {
            throw new IllegalStateException(
                    "Settlement ops report is available only after STGS/RTGS confirmation. "
                    + "cycleRef=" + cycleRef + ", currentStatus=" + cycle.status());
        }

        List<Position> positions = loadPositions(cycle.id());
        List<Instruction> instructions = loadInstructions(cycle.id());
        List<Transfer> transfers = loadTransfers(cycle.id());
        List<ReportArtifact> reportArtifacts = loadReportArtifacts(cycle.id());
        Summary summary = summarize(cycle, positions, instructions, transfers);

        return new OpsSettlementReport(
                LocalDateTime.now(),
                cycle,
                summary,
                positions,
                instructions,
                transfers,
                reportArtifacts);
    }

    @Transactional(readOnly = true)
    public String loadCsv(String cycleRef) {
        OpsSettlementReport report = load(cycleRef);
        StringBuilder csv = new StringBuilder();

        csv.append("section,cycleRef,settlementDate,status,settledAt,itemCount,transferCount,totalDebit,totalCredit,currency\n");
        csv.append(csvRow(
                "SUMMARY",
                report.cycle().cycleRef(),
                string(report.cycle().settlementDate()),
                report.cycle().status(),
                string(report.cycle().settledAt()),
                string(report.summary().itemCount()),
                string(report.summary().transferCount()),
                money(report.summary().totalDebit()),
                money(report.summary().totalCredit()),
                report.summary().currency()));

        csv.append("\nPOSITIONS\n");
        csv.append("bankCode,currency,debitAmount,creditAmount,netPosition,transactionCount,status,settledAt\n");
        for (Position position : report.positions()) {
            csv.append(csvRow(
                    position.bankCode(),
                    position.currency(),
                    money(position.debitAmount()),
                    money(position.creditAmount()),
                    money(position.netPosition()),
                    string(position.transactionCount()),
                    position.status(),
                    string(position.settledAt())));
        }

        csv.append("\nINSTRUCTIONS\n");
        csv.append("instructionRef,debtorPspId,creditorPspId,currency,netAmount,status,rtgsMsgId,sentAt,confirmedAt\n");
        for (Instruction instruction : report.instructions()) {
            csv.append(csvRow(
                    instruction.instructionRef(),
                    instruction.debtorPspId(),
                    instruction.creditorPspId(),
                    instruction.currency(),
                    money(instruction.netAmount()),
                    instruction.status(),
                    instruction.rtgsMsgId(),
                    string(instruction.sentAt()),
                    string(instruction.confirmedAt())));
        }

        csv.append("\nTRANSFERS\n");
        csv.append("transferRef,clientTransferId,sourceBank,destinationBank,amount,currency,status,confirmationStatus,settlementConfidence,externalReference,createdAt,settledAt\n");
        for (Transfer transfer : report.transfers()) {
            csv.append(csvRow(
                    transfer.transferRef(),
                    transfer.clientTransferId(),
                    transfer.sourceBank(),
                    transfer.destinationBank(),
                    money(transfer.amount()),
                    transfer.currency(),
                    transfer.status(),
                    transfer.confirmationStatus(),
                    transfer.settlementConfidence(),
                    transfer.externalReference(),
                    string(transfer.createdAt()),
                    string(transfer.settledAt())));
        }

        csv.append("\nREPORT_ARTIFACTS\n");
        csv.append("reportRef,pspId,reportType,generatedAt\n");
        for (ReportArtifact artifact : report.reportArtifacts()) {
            csv.append(csvRow(
                    artifact.reportRef(),
                    artifact.pspId(),
                    artifact.reportType(),
                    string(artifact.generatedAt())));
        }

        return csv.toString();
    }

    private Cycle loadCycle(String cycleRef) {
        return jdbc.query("""
                SELECT id, cycle_ref, settlement_date, cycle_number, status,
                       opened_at, closed_at, settled_at
                  FROM settlement_cycles
                 WHERE cycle_ref = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Settlement cycle not found: " + cycleRef);
                    }
                    return new Cycle(
                            rs.getLong("id"),
                            rs.getString("cycle_ref"),
                            rs.getObject("settlement_date", LocalDate.class),
                            rs.getInt("cycle_number"),
                            rs.getString("status"),
                            rs.getObject("opened_at", LocalDateTime.class),
                            rs.getObject("closed_at", LocalDateTime.class),
                            rs.getObject("settled_at", LocalDateTime.class));
                },
                cycleRef);
    }

    private List<Position> loadPositions(Long cycleId) {
        return jdbc.query("""
                SELECT bank_code, currency, debit_amount, credit_amount,
                       net_position, transaction_count, status, settled_at
                  FROM settlement_positions
                 WHERE cycle_id = ?
                 ORDER BY bank_code ASC, currency ASC
                """,
                (rs, rowNum) -> new Position(
                        rs.getString("bank_code"),
                        rs.getString("currency"),
                        rs.getBigDecimal("debit_amount"),
                        rs.getBigDecimal("credit_amount"),
                        rs.getBigDecimal("net_position"),
                        rs.getInt("transaction_count"),
                        rs.getString("status"),
                        rs.getObject("settled_at", LocalDateTime.class)),
                cycleId);
    }

    private List<Instruction> loadInstructions(Long cycleId) {
        return jdbc.query("""
                SELECT instruction_ref, debtor_psp_id, creditor_psp_id, currency,
                       net_amount, status, rtgs_msg_id, sent_at, confirmed_at
                  FROM settlement_instructions
                 WHERE cycle_id = ?
                 ORDER BY instruction_ref ASC
                """,
                (rs, rowNum) -> new Instruction(
                        rs.getString("instruction_ref"),
                        rs.getString("debtor_psp_id"),
                        rs.getString("creditor_psp_id"),
                        rs.getString("currency"),
                        rs.getBigDecimal("net_amount"),
                        rs.getString("status"),
                        rs.getString("rtgs_msg_id"),
                        rs.getObject("sent_at", LocalDateTime.class),
                        rs.getObject("confirmed_at", LocalDateTime.class)),
                cycleId);
    }

    private List<Transfer> loadTransfers(Long cycleId) {
        return jdbc.query("""
                SELECT DISTINCT t.transaction_ref, t.client_transaction_id,
                       t.source_bank, t.destination_bank, t.amount, t.currency,
                       t.status, t.confirmation_status, t.settlement_confidence,
                       t.external_reference, t.created_at, t.settled_at
                  FROM settlement_items si
                  JOIN transactions t ON t.transaction_ref = si.transaction_ref
                 WHERE si.cycle_id = ?
                 ORDER BY t.transaction_ref ASC
                """,
                (rs, rowNum) -> new Transfer(
                        rs.getString("transaction_ref"),
                        rs.getString("client_transaction_id"),
                        rs.getString("source_bank"),
                        rs.getString("destination_bank"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getString("confirmation_status"),
                        rs.getString("settlement_confidence"),
                        rs.getString("external_reference"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("settled_at", LocalDateTime.class)),
                cycleId);
    }

    private List<ReportArtifact> loadReportArtifacts(Long cycleId) {
        return jdbc.query("""
                SELECT report_ref, psp_id, report_type, generated_at
                  FROM settlement_reports
                 WHERE cycle_id = ?
                 ORDER BY psp_id ASC, report_type ASC
                """,
                (rs, rowNum) -> new ReportArtifact(
                        rs.getString("report_ref"),
                        rs.getString("psp_id"),
                        rs.getString("report_type"),
                        rs.getObject("generated_at", LocalDateTime.class)),
                cycleId);
    }

    private Summary summarize(Cycle cycle, List<Position> positions, List<Instruction> instructions,
            List<Transfer> transfers) {
        BigDecimal totalDebit = positions.stream()
                .map(Position::debitAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = positions.stream()
                .map(Position::creditAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = positions.stream()
                .map(Position::currency)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse("LAK");
        int itemCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM settlement_items WHERE cycle_id = ?",
                Integer.class,
                cycle.id());
        long confirmedInstructions = instructions.stream()
                .filter(i -> "CONFIRMED".equals(i.status()))
                .count();
        return new Summary(
                itemCount,
                transfers.size(),
                positions.size(),
                instructions.size(),
                (int) confirmedInstructions,
                totalDebit,
                totalCredit,
                currency);
    }

    private String csvRow(String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(csv(value));
        }
        return joiner + "\n";
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    public record OpsSettlementReport(
            LocalDateTime generatedAt,
            Cycle cycle,
            Summary summary,
            List<Position> positions,
            List<Instruction> instructions,
            List<Transfer> transfers,
            List<ReportArtifact> reportArtifacts) {}

    public record Cycle(
            Long id,
            String cycleRef,
            LocalDate settlementDate,
            int cycleNumber,
            String status,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            LocalDateTime settledAt) {}

    public record Summary(
            int itemCount,
            int transferCount,
            int participantCount,
            int instructionCount,
            int confirmedInstructionCount,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String currency) {}

    public record Position(
            String bankCode,
            String currency,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal netPosition,
            int transactionCount,
            String status,
            LocalDateTime settledAt) {}

    public record Instruction(
            String instructionRef,
            String debtorPspId,
            String creditorPspId,
            String currency,
            BigDecimal netAmount,
            String status,
            String rtgsMsgId,
            LocalDateTime sentAt,
            LocalDateTime confirmedAt) {}

    public record Transfer(
            String transferRef,
            String clientTransferId,
            String sourceBank,
            String destinationBank,
            BigDecimal amount,
            String currency,
            String status,
            String confirmationStatus,
            String settlementConfidence,
            String externalReference,
            LocalDateTime createdAt,
            LocalDateTime settledAt) {}

    public record ReportArtifact(
            String reportRef,
            String pspId,
            String reportType,
            LocalDateTime generatedAt) {}
}
