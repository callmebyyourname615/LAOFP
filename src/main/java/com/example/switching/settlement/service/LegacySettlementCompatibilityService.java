package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.exception.SettlementInstructionNotFoundException;
import com.example.switching.settlement.repository.SettlementInstructionRepository;

@Service
public class LegacySettlementCompatibilityService {

    private static final String ENTITY = "LEGACY_SETTLEMENT_COMPATIBILITY";

    private final SettlementOpsReportService opsReportService;
    private final SettlementInstructionRepository instructionRepository;
    private final AuditLogService auditLogService;

    public LegacySettlementCompatibilityService(SettlementOpsReportService opsReportService,
                                                SettlementInstructionRepository instructionRepository,
                                                AuditLogService auditLogService) {
        this.opsReportService = opsReportService;
        this.instructionRepository = instructionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LegacyFile exportSettlementFile(String cycleRef, LegacySettlementFormat format, String actor) {
        SettlementOpsReportService.OpsSettlementReport report = opsReportService.load(cycleRef);
        String content = switch (format) {
            case SETTLEMENT_FILE -> settlementFile(report);
            case SETTLEMENT_REPORT -> reportCsv(report, "SETTLEMENT");
            case SETTLEMENT_REPORT_LAPNET -> reportCsv(report, "LAPNET_SETTLEMENT");
            case SETTLEMENT_REPORT_V2 -> reportV2Csv(report, "SETTLEMENT_V2");
            case FINLINK_SETTLEMENT_REPORT -> reportCsv(report, "FINLINK_SETTLEMENT");
            case FINLINK_SETTLEMENT_REPORT_V2 -> reportV2Csv(report, "FINLINK_SETTLEMENT_V2");
            case FINLINK_LAPNET_SETTLEMENT_REPORT -> reportCsv(report, "FINLINK_LAPNET_SETTLEMENT");
        };
        audit(format.name(), cycleRef, actor, content);
        return new LegacyFile(filename(cycleRef, format), content);
    }

    @Transactional
    public RtgsJournal exportRtgsJournal(String instructionRef, String rtgsMsgId, String actor) {
        SettlementInstructionEntity instruction = findInstruction(instructionRef, rtgsMsgId);
        RtgsJournal journal = new RtgsJournal(
                instruction.getInstructionRef(),
                instruction.getRtgsMsgId(),
                instruction.getDebtorPspId(),
                instruction.getCreditorPspId(),
                instruction.getCurrency(),
                instruction.getNetAmount(),
                instruction.getStatus(),
                instruction.getSentAt(),
                instruction.getConfirmedAt(),
                instruction.getLastError(),
                instruction.getRtgsRequestPayload(),
                instruction.getRtgsResponsePayload());
        auditLogService.log("LEGACY_RTGS_JOURNAL_EXPORTED", ENTITY, journal.instructionRef(), actorOrSource(actor),
                Map.of(
                        "instructionRef", journal.instructionRef(),
                        "rtgsMsgId", journal.rtgsMsgId() != null ? journal.rtgsMsgId() : "",
                        "status", journal.status()));
        return journal;
    }

    private SettlementInstructionEntity findInstruction(String instructionRef, String rtgsMsgId) {
        if (instructionRef != null && !instructionRef.isBlank()) {
            return instructionRepository.findByInstructionRef(instructionRef)
                    .orElseThrow(() -> new SettlementInstructionNotFoundException(instructionRef));
        }
        if (rtgsMsgId != null && !rtgsMsgId.isBlank()) {
            return instructionRepository.findByRtgsMsgId(rtgsMsgId)
                    .orElseThrow(() -> new SettlementInstructionNotFoundException("rtgsMsgId=" + rtgsMsgId));
        }
        throw new SettlementInstructionNotFoundException("instructionRef or rtgsMsgId is required");
    }

    private String settlementFile(SettlementOpsReportService.OpsSettlementReport report) {
        StringBuilder body = new StringBuilder();
        appendPipe(body, "HDR", "LMPS_SETTLEMENT_FILE", "1", report.cycle().cycleRef(),
                string(report.cycle().settlementDate()), report.cycle().status(),
                report.summary().currency(), string(report.generatedAt()));
        for (SettlementOpsReportService.Position position : report.positions()) {
            appendPipe(body, "POS", position.bankCode(), position.currency(),
                    money(position.debitAmount()), money(position.creditAmount()),
                    money(position.netPosition()), string(position.transactionCount()), position.status());
        }
        for (SettlementOpsReportService.Instruction instruction : report.instructions()) {
            appendPipe(body, "INS", instruction.instructionRef(), instruction.debtorPspId(),
                    instruction.creditorPspId(), instruction.currency(), money(instruction.netAmount()),
                    instruction.status(), instruction.rtgsMsgId(), string(instruction.sentAt()),
                    string(instruction.confirmedAt()));
        }
        appendPipe(body, "TRL", string(report.summary().participantCount()),
                string(report.summary().instructionCount()), string(report.summary().transferCount()),
                money(report.summary().totalDebit()), money(report.summary().totalCredit()));
        appendPipe(body, "CHK", sha256(body.toString()));
        return body.toString();
    }

    private String reportCsv(SettlementOpsReportService.OpsSettlementReport report, String reportType) {
        StringBuilder csv = new StringBuilder();
        csv.append("reportType,cycleRef,settlementDate,status,currency,totalDebit,totalCredit,participantCount,instructionCount,transferCount\n");
        csv.append(csvRow(reportType, report.cycle().cycleRef(), string(report.cycle().settlementDate()),
                report.cycle().status(), report.summary().currency(), money(report.summary().totalDebit()),
                money(report.summary().totalCredit()), string(report.summary().participantCount()),
                string(report.summary().instructionCount()), string(report.summary().transferCount())));
        csv.append("\npositions\n");
        csv.append("bankCode,currency,debitAmount,creditAmount,netPosition,transactionCount,status\n");
        for (SettlementOpsReportService.Position position : report.positions()) {
            csv.append(csvRow(position.bankCode(), position.currency(), money(position.debitAmount()),
                    money(position.creditAmount()), money(position.netPosition()),
                    string(position.transactionCount()), position.status()));
        }
        return csv.toString();
    }

    private String reportV2Csv(SettlementOpsReportService.OpsSettlementReport report, String reportType) {
        StringBuilder csv = new StringBuilder(reportCsv(report, reportType));
        csv.append("\ninstructions\n");
        csv.append("instructionRef,debtorPspId,creditorPspId,currency,netAmount,status,rtgsMsgId,sentAt,confirmedAt\n");
        for (SettlementOpsReportService.Instruction instruction : report.instructions()) {
            csv.append(csvRow(instruction.instructionRef(), instruction.debtorPspId(),
                    instruction.creditorPspId(), instruction.currency(), money(instruction.netAmount()),
                    instruction.status(), instruction.rtgsMsgId(), string(instruction.sentAt()),
                    string(instruction.confirmedAt())));
        }
        return csv.toString();
    }

    private void appendPipe(StringBuilder builder, String... values) {
        StringJoiner joiner = new StringJoiner("|");
        for (String value : values) {
            joiner.add(pipe(value));
        }
        builder.append(joiner).append('\n');
    }

    private String csvRow(String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add("\"" + string(value).replace("\"", "\"\"") + "\"");
        }
        return joiner + "\n";
    }

    private String pipe(String value) {
        return string(value).replace("|", "/").replace("\n", " ").replace("\r", " ");
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String filename(String cycleRef, LegacySettlementFormat format) {
        String suffix = switch (format) {
            case SETTLEMENT_FILE -> "settlementfile.txt";
            case SETTLEMENT_REPORT -> "report-settlement.csv";
            case SETTLEMENT_REPORT_LAPNET -> "report-settlement-lapnet.csv";
            case SETTLEMENT_REPORT_V2 -> "report-settlement-v2.csv";
            case FINLINK_SETTLEMENT_REPORT -> "finlink-report-settlement.csv";
            case FINLINK_SETTLEMENT_REPORT_V2 -> "finlink-report-settlement-v2.csv";
            case FINLINK_LAPNET_SETTLEMENT_REPORT -> "finlink-report-lapnet-settlement.csv";
        };
        return cycleRef + "-" + suffix;
    }

    private void audit(String format, String cycleRef, String actor, String content) {
        auditLogService.log("LEGACY_SETTLEMENT_FILE_EXPORTED", ENTITY, cycleRef, actorOrSource(actor),
                Map.of(
                        "cycleRef", cycleRef,
                        "format", format,
                        "sha256", sha256(content)));
    }

    private String actorOrSource(String actor) {
        return actor != null && !actor.isBlank() ? actor : "SETTLEMENT";
    }

    public enum LegacySettlementFormat {
        SETTLEMENT_FILE,
        SETTLEMENT_REPORT,
        SETTLEMENT_REPORT_LAPNET,
        SETTLEMENT_REPORT_V2,
        FINLINK_SETTLEMENT_REPORT,
        FINLINK_SETTLEMENT_REPORT_V2,
        FINLINK_LAPNET_SETTLEMENT_REPORT
    }

    public record LegacyFile(String filename, String content) {}

    public record RtgsJournal(
            String instructionRef,
            String rtgsMsgId,
            String debtorPspId,
            String creditorPspId,
            String currency,
            BigDecimal netAmount,
            String status,
            LocalDateTime sentAt,
            LocalDateTime confirmedAt,
            String lastError,
            String requestPayload,
            String responsePayload) {}
}
