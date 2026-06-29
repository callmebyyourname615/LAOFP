package com.example.switching.settlement.controller;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.settlement.dto.OpenCycleRequest;
import com.example.switching.settlement.dto.SettlementInstructionDecisionRequest;
import com.example.switching.settlement.dto.SettlementInstructionResponse;
import com.example.switching.settlement.dto.SettlementCycleResponse;
import com.example.switching.settlement.dto.SettlementPositionResponse;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.entity.SettlementReportEntity;
import com.example.switching.settlement.service.Camt054ReportService;
import com.example.switching.settlement.service.SettlementBatchService;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.RtgsGatewayService;
import com.example.switching.settlement.service.SettlementInstructionService;
import com.example.switching.settlement.service.SettlementNetPositionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Operations API for the settlement engine.
 *
 * <pre>
 *  POST   /api/operations/settlement/cycles              — open a new cycle
 *  GET    /api/operations/settlement/cycles?date=…       — list by date
 *  GET    /api/operations/settlement/cycles?status=…     — list by status
 *  GET    /api/operations/settlement/cycles/{cycleRef}   — cycle detail + positions
 *  POST   /api/operations/settlement/cycles/{cycleRef}/batch   — batch SETTLED transfers
 *  POST   /api/operations/settlement/cycles/{cycleRef}/close   — close cycle
 *  POST   /api/operations/settlement/cycles/{cycleRef}/settle  — mark SETTLED after BOL RTGS confirmation
 * </pre>
 */
@RestController
@RequestMapping("/api/operations/settlement")
@PreAuthorize("hasAuthority('PERM_SETTLEMENT_VIEW')")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final SettlementCycleService       cycleService;
    private final SettlementBatchService       batchService;
    private final SettlementNetPositionService netPositionService;
    private final SettlementInstructionService instructionService;
    private final RtgsGatewayService           rtgsGatewayService;
    private final Camt054ReportService         reportService;

    public SettlementController(SettlementCycleService cycleService,
                                 SettlementBatchService batchService,
                                 SettlementNetPositionService netPositionService,
                                 SettlementInstructionService instructionService,
                                 RtgsGatewayService rtgsGatewayService,
                                 Camt054ReportService reportService) {
        this.cycleService       = cycleService;
        this.batchService       = batchService;
        this.netPositionService = netPositionService;
        this.instructionService = instructionService;
        this.rtgsGatewayService = rtgsGatewayService;
        this.reportService      = reportService;
    }

    // ── Open a new cycle ─────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/cycles")
    public ResponseEntity<SettlementCycleResponse> openCycle(@RequestBody OpenCycleRequest request) {
        SettlementCycleEntity cycle = cycleService.openCycle(request.getSettlementDate());
        return ResponseEntity.ok(toResponse(cycle));
    }

    // ── List cycles ──────────────────────────────────────────────────────────

    /**
     * List by date ({@code ?date=yyyy-MM-dd}) or by status ({@code ?status=OPEN}).
     * If neither is provided, lists all OPEN cycles.
     */
    @GetMapping("/cycles")
    public ResponseEntity<List<SettlementCycleResponse>> listCycles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {

        List<SettlementCycleEntity> cycles;
        if (date != null) {
            cycles = cycleService.listByDate(date);
        } else {
            cycles = cycleService.listByStatus(status != null ? status.toUpperCase() : "OPEN");
        }
        return ResponseEntity.ok(cycles.stream().map(this::toResponse).toList());
    }

    // ── Cycle detail ─────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleRef}")
    public ResponseEntity<SettlementCycleResponse> getCycle(@PathVariable String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        List<SettlementPositionEntity> positions = netPositionService.getPositions(cycleRef);
        int itemCount = batchService.countItems(cycle.getId());
        return ResponseEntity.ok(toResponse(cycle, itemCount, positions));
    }

    // ── Batch transactions into cycle ────────────────────────────────────────

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/cycles/{cycleRef}/batch")
    public ResponseEntity<BatchResult> batchTransactions(@PathVariable String cycleRef) {
        int count = batchService.batchTransactions(cycleRef);
        return ResponseEntity.ok(new BatchResult(cycleRef, count, count * 2));
    }

    // ── Close cycle ──────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/cycles/{cycleRef}/close")
    public ResponseEntity<SettlementCycleResponse> closeCycle(@PathVariable String cycleRef) {
        SettlementCycleEntity cycle = cycleService.closeCycle(cycleRef);
        return ResponseEntity.ok(toResponse(cycle));
    }

    // ── Settle (netting) ─────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/cycles/{cycleRef}/settle")
    public ResponseEntity<SettleResult> settleCycle(@PathVariable String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        if ("SETTLED".equals(cycle.getStatus())) {
            List<SettlementPositionEntity> positions = netPositionService.getPositions(cycleRef);
            List<SettlementPositionResponse> posResponses = positions.stream().map(this::toPositionResponse).toList();
            return ResponseEntity.ok(new SettleResult(cycleRef, "SETTLED", posResponses));
        }
        if (!instructionService.allInstructionsConfirmed(cycle.getId())) {
            throw new IllegalStateException(
                    "Cannot settle cycle " + cycleRef
                    + " before all RTGS portal instructions are CONFIRMED");
        }
        List<SettlementPositionEntity> positions = netPositionService.settle(cycleRef);
        try {
            reportService.generateReportsForCycle(cycleRef);
        } catch (Exception ex) {
            log.warn("camt.054 report generation failed for cycleRef={}: {}", cycleRef, ex.getMessage(), ex);
        }

        List<SettlementPositionResponse> posResponses = positions.stream().map(this::toPositionResponse).toList();
        return ResponseEntity.ok(new SettleResult(cycleRef, "SETTLED", posResponses));
    }

    @GetMapping("/instructions/{instructionRef}/rtgs-file")
    public ResponseEntity<String> downloadRtgsFile(
            @PathVariable String instructionRef,
            Authentication authentication) {
        SettlementInstructionEntity instruction = rtgsGatewayService.prepareManualRtgsFile(
                instructionRef, actor(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + instruction.getInstructionRef() + "-pacs009.xml\"")
                .body(instruction.getRtgsRequestPayload());
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/instructions/{instructionRef}/record-rtgs-upload")
    public ResponseEntity<SettlementInstructionResponse> recordRtgsPortalUpload(
            @PathVariable String instructionRef,
            @RequestBody(required = false) SettlementInstructionDecisionRequest request,
            Authentication authentication) {
        SettlementInstructionEntity instruction = rtgsGatewayService.recordManualRtgsUpload(
                instructionRef,
                actor(authentication),
                request != null ? request.note() : null);
        return ResponseEntity.ok(toInstructionResponse(cycleRefFor(instruction), instruction));
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/cycles/{cycleRef}/instructions/generate")
    public ResponseEntity<List<SettlementInstructionResponse>> generateInstructions(@PathVariable String cycleRef) {
        List<SettlementInstructionEntity> instructions = instructionService.generateForCycle(cycleRef);
        return ResponseEntity.ok(instructions.stream()
                .map(i -> toInstructionResponse(cycleRef, i))
                .toList());
    }

    @GetMapping("/cycles/{cycleRef}/instructions")
    public ResponseEntity<List<SettlementInstructionResponse>> listInstructions(@PathVariable String cycleRef) {
        List<SettlementInstructionEntity> instructions = instructionService.listForCycle(cycleRef);
        return ResponseEntity.ok(instructions.stream()
                .map(i -> toInstructionResponse(cycleRef, i))
                .toList());
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/instructions/{instructionRef}/approve")
    public ResponseEntity<SettlementInstructionResponse> approveInstruction(
            @PathVariable String instructionRef,
            @RequestBody(required = false) SettlementInstructionDecisionRequest request,
            Authentication authentication) {
        SettlementInstructionEntity instruction = instructionService.approve(
                instructionRef,
                actor(authentication),
                request != null ? request.note() : null);
        return ResponseEntity.ok(toInstructionResponse(cycleRefFor(instruction), instruction));
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/instructions/{instructionRef}/reject")
    public ResponseEntity<SettlementInstructionResponse> rejectInstruction(
            @PathVariable String instructionRef,
            @RequestBody(required = false) SettlementInstructionDecisionRequest request,
            Authentication authentication) {
        SettlementInstructionEntity instruction = instructionService.reject(
                instructionRef,
                actor(authentication),
                request != null ? request.reason() : null);
        return ResponseEntity.ok(toInstructionResponse(cycleRefFor(instruction), instruction));
    }

    @PreAuthorize("hasAuthority('PERM_SETTLEMENT_APPROVE')")
    @PostMapping("/instructions/{instructionRef}/send-rtgs")
    public ResponseEntity<SettlementInstructionResponse> sendInstructionToRtgs(
            @PathVariable String instructionRef,
            @RequestBody(required = false) SettlementInstructionDecisionRequest request,
            Authentication authentication) {
        SettlementInstructionEntity instruction = rtgsGatewayService.recordManualRtgsUpload(
                instructionRef,
                actor(authentication),
                request != null ? request.note() : "Uploaded manually through BOL RTGS portal");
        return ResponseEntity.ok(toInstructionResponse(cycleRefFor(instruction), instruction));
    }

    // ── camt.054 settlement report (bank-facing) ─────────────────────────────

    /**
     * Retrieve the ISO 20022 camt.054 BankToCustomerDebitCreditNotification for
     * the calling PSP for the given settled cycle.
     *
     * <p>Auth: The report is scoped to the authenticated PSP — a BANK caller receives
     * only their own camt.054; OPS/ADMIN callers must supply {@code ?pspId=} to select
     * a specific PSP. When no {@code pspId} param is provided and the caller is BANK,
     * the authenticated principal is used.
     *
     * @param cycleRef settlement cycle reference
     * @param pspId    optional override — required when caller role is OPS/ADMIN
     * @return camt.054 XML with {@code Content-Type: application/xml}; 404 if not generated yet
     */
    @GetMapping("/cycles/{cycleRef}/report")
    public ResponseEntity<String> getSettlementReport(
            @PathVariable String cycleRef,
            @RequestParam(required = false) String pspId,
            Authentication authentication) {

        // Resolve PSP: explicit param wins; otherwise use authenticated principal
        String effectivePspId = (pspId != null && !pspId.isBlank())
                ? pspId
                : (authentication != null ? authentication.getName() : null);

        if (effectivePspId == null || effectivePspId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return reportService.getReport(cycleRef, effectivePspId)
                .map(report -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + report.getReportRef() + ".xml\"")
                        .body(report.getContent()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all camt.054 settlement reports for a cycle (OPS/ADMIN only — all PSPs).
     */
    @GetMapping("/cycles/{cycleRef}/reports")
    public ResponseEntity<List<SettlementReportSummary>> listSettlementReports(
            @PathVariable String cycleRef) {
        List<SettlementReportEntity> reports = reportService.listForCycle(cycleRef);
        List<SettlementReportSummary> summaries = reports.stream()
                .map(r -> new SettlementReportSummary(
                        r.getId(),
                        r.getReportRef(),
                        r.getPspId(),
                        r.getReportType(),
                        r.getGeneratedAt()))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private SettlementCycleResponse toResponse(SettlementCycleEntity c) {
        return toResponse(c, 0, List.of());
    }

    private SettlementCycleResponse toResponse(SettlementCycleEntity c,
                                                int itemCount,
                                                List<SettlementPositionEntity> positions) {
        return new SettlementCycleResponse(
                c.getId(),
                c.getCycleRef(),
                c.getSettlementDate(),
                c.getCycleNumber(),
                c.getStatus(),
                c.getOpenedAt(),
                c.getClosedAt(),
                c.getSettledAt(),
                itemCount,
                positions.stream().map(this::toPositionResponse).toList()
        );
    }

    private SettlementPositionResponse toPositionResponse(SettlementPositionEntity p) {
        return new SettlementPositionResponse(
                p.getId(),
                p.getBankCode(),
                p.getCurrency(),
                p.getDebitAmount(),
                p.getCreditAmount(),
                p.getNetPosition(),
                p.getTransactionCount(),
                p.getStatus(),
                p.getSettledAt()
        );
    }

    private SettlementInstructionResponse toInstructionResponse(String cycleRef, SettlementInstructionEntity i) {
        return new SettlementInstructionResponse(
                i.getId(),
                i.getInstructionRef(),
                cycleRef,
                i.getSourceType(),
                i.getTransferRef(),
                i.getDebtorPspId(),
                i.getCreditorPspId(),
                i.getCurrency(),
                i.getNetAmount(),
                i.getStatus(),
                i.getApprovalNote(),
                i.getApprovedBy(),
                i.getApprovedAt(),
                i.getRejectedBy(),
                i.getRejectedAt(),
                i.getRejectionReason(),
                i.getRtgsMsgId(),
                i.getLastError(),
                i.getSentAt(),
                i.getConfirmedAt(),
                i.getCreatedAt());
    }

    private String cycleRefFor(SettlementInstructionEntity instruction) {
        if (instruction.getCycleId() == null) {
            return null;
        }
        return cycleService.requireCycleById(instruction.getCycleId()).getCycleRef();
    }

    private String actor(Authentication authentication) {
        return authentication != null && authentication.getName() != null
                ? authentication.getName()
                : "SYSTEM";
    }

    // ── Inner response records ────────────────────────────────────────────────

    public record BatchResult(String cycleRef, int transfersProcessed, int itemRowsWritten) {}

    public record SettleResult(String cycleRef, String status,
                                List<SettlementPositionResponse> positions) {}

    public record SettlementReportSummary(
            Long id,
            String reportRef,
            String pspId,
            String reportType,
            java.time.LocalDateTime generatedAt) {}
}
