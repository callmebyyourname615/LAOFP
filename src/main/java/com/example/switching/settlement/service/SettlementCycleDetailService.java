package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.settlement.dto.SettlementCycleDetailResponse;
import com.example.switching.settlement.dto.SettlementCycleResponse;
import com.example.switching.settlement.dto.SettlementCycleSummaryResponse;
import com.example.switching.settlement.dto.SettlementInstructionResponse;
import com.example.switching.settlement.dto.SettlementPositionResponse;
import com.example.switching.settlement.dto.SettlementReportArtifactResponse;
import com.example.switching.settlement.dto.SettlementTimelineItemResponse;
import com.example.switching.settlement.dto.SettlementTimelineResponse;
import com.example.switching.settlement.dto.SettlementTransferItemResponse;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;

@Service
public class SettlementCycleDetailService {

    private static final int TIMELINE_PREVIEW_LIMIT = 10;

    private final SettlementCycleService cycleService;
    private final SettlementBatchService batchService;
    private final SettlementNetPositionService netPositionService;
    private final SettlementInstructionService instructionService;
    private final SettlementTimelineService timelineService;
    private final JdbcTemplate jdbc;

    public SettlementCycleDetailService(
            SettlementCycleService cycleService,
            SettlementBatchService batchService,
            SettlementNetPositionService netPositionService,
            SettlementInstructionService instructionService,
            SettlementTimelineService timelineService,
            JdbcTemplate jdbc) {
        this.cycleService = cycleService;
        this.batchService = batchService;
        this.netPositionService = netPositionService;
        this.instructionService = instructionService;
        this.timelineService = timelineService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SettlementCycleDetailResponse detail(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        int itemCount = batchService.countItems(cycle.getId());
        List<SettlementPositionEntity> positionEntities = netPositionService.getPositions(cycleRef);
        List<SettlementInstructionEntity> instructionEntities = instructionService.listForCycle(cycleRef);
        List<SettlementTransferItemResponse> transfers = loadTransfers(cycle.getId());
        List<SettlementReportArtifactResponse> reports = loadReports(cycle.getId());
        SettlementTimelineResponse timeline = timelineService.timeline(cycleRef);
        SettlementTimelineResponse timelinePreview = preview(timeline);

        List<SettlementPositionResponse> positions = positionEntities.stream()
                .map(this::toPositionResponse)
                .toList();
        List<SettlementInstructionResponse> instructions = instructionEntities.stream()
                .map(i -> toInstructionResponse(cycleRef, i))
                .toList();

        SettlementCycleResponse cycleResponse = toCycleResponse(cycle, itemCount, positions);
        SettlementCycleSummaryResponse summary = summarize(
                itemCount,
                positions,
                instructions,
                transfers,
                timeline);

        return new SettlementCycleDetailResponse(
                LocalDateTime.now(),
                cycleResponse,
                summary,
                positions,
                instructions,
                transfers,
                reports,
                timelinePreview);
    }

    private List<SettlementTransferItemResponse> loadTransfers(Long cycleId) {
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
                (rs, rowNum) -> new SettlementTransferItemResponse(
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
                        time(rs, "created_at"),
                        time(rs, "settled_at")),
                cycleId);
    }

    private List<SettlementReportArtifactResponse> loadReports(Long cycleId) {
        return jdbc.query("""
                SELECT id, report_ref, psp_id, report_type, generated_at
                  FROM settlement_reports
                 WHERE cycle_id = ?
                 ORDER BY psp_id ASC, report_type ASC
                """,
                (rs, rowNum) -> new SettlementReportArtifactResponse(
                        rs.getLong("id"),
                        rs.getString("report_ref"),
                        rs.getString("psp_id"),
                        rs.getString("report_type"),
                        time(rs, "generated_at")),
                cycleId);
    }

    private SettlementTimelineResponse preview(SettlementTimelineResponse timeline) {
        List<SettlementTimelineItemResponse> items = timeline.items();
        int from = Math.max(0, items.size() - TIMELINE_PREVIEW_LIMIT);
        return new SettlementTimelineResponse(
                timeline.cycleRef(),
                timeline.count(),
                items.subList(from, items.size()));
    }

    private SettlementCycleSummaryResponse summarize(
            int itemCount,
            List<SettlementPositionResponse> positions,
            List<SettlementInstructionResponse> instructions,
            List<SettlementTransferItemResponse> transfers,
            SettlementTimelineResponse timeline) {
        BigDecimal totalDebit = positions.stream()
                .map(SettlementPositionResponse::debitAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = positions.stream()
                .map(SettlementPositionResponse::creditAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netImbalance = totalCredit.subtract(totalDebit);
        String currency = positions.stream()
                .map(SettlementPositionResponse::currency)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElseGet(() -> transfers.stream()
                        .map(SettlementTransferItemResponse::currency)
                        .filter(v -> v != null && !v.isBlank())
                        .findFirst()
                        .orElse("LAK"));
        LocalDateTime latestEventAt = timeline.items().stream()
                .map(SettlementTimelineItemResponse::createdAt)
                .filter(v -> v != null)
                .reduce((previous, current) -> current)
                .orElse(null);

        return new SettlementCycleSummaryResponse(
                itemCount,
                transfers.size(),
                positions.size(),
                instructions.size(),
                countInstructions(instructions, "PENDING_APPROVAL"),
                countInstructions(instructions, "APPROVED"),
                countInstructions(instructions, "SENT_RTGS"),
                countInstructions(instructions, "CONFIRMED"),
                countInstructions(instructions, "FAILED"),
                totalDebit,
                totalCredit,
                netImbalance,
                currency,
                latestEventAt);
    }

    private int countInstructions(List<SettlementInstructionResponse> instructions, String status) {
        return (int) instructions.stream()
                .filter(i -> status.equals(i.status()))
                .count();
    }

    private SettlementCycleResponse toCycleResponse(
            SettlementCycleEntity cycle,
            int itemCount,
            List<SettlementPositionResponse> positions) {
        return new SettlementCycleResponse(
                cycle.getId(),
                cycle.getCycleRef(),
                cycle.getSettlementDate(),
                cycle.getCycleNumber(),
                cycle.getStatus(),
                cycle.getOpenedAt(),
                cycle.getClosedAt(),
                cycle.getSettledAt(),
                itemCount,
                positions);
    }

    private SettlementPositionResponse toPositionResponse(SettlementPositionEntity position) {
        return new SettlementPositionResponse(
                position.getId(),
                position.getBankCode(),
                position.getCurrency(),
                position.getDebitAmount(),
                position.getCreditAmount(),
                position.getNetPosition(),
                position.getTransactionCount(),
                position.getStatus(),
                position.getSettledAt());
    }

    private SettlementInstructionResponse toInstructionResponse(String cycleRef, SettlementInstructionEntity instruction) {
        return new SettlementInstructionResponse(
                instruction.getId(),
                instruction.getInstructionRef(),
                cycleRef,
                instruction.getSourceType(),
                instruction.getTransferRef(),
                instruction.getDebtorPspId(),
                instruction.getCreditorPspId(),
                instruction.getCurrency(),
                instruction.getNetAmount(),
                instruction.getStatus(),
                instruction.getApprovalNote(),
                instruction.getApprovedBy(),
                instruction.getApprovedAt(),
                instruction.getRejectedBy(),
                instruction.getRejectedAt(),
                instruction.getRejectionReason(),
                instruction.getRtgsMsgId(),
                instruction.getLastError(),
                instruction.getSentAt(),
                instruction.getConfirmedAt(),
                instruction.getCreatedAt());
    }

    private LocalDateTime time(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
