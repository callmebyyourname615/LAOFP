package com.example.switching.reconciliation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.reconciliation.dto.CreateReconFileRequest;
import com.example.switching.reconciliation.dto.ReconDiscrepancyReport;
import com.example.switching.reconciliation.dto.ReconFileResponse;
import com.example.switching.reconciliation.dto.ReconItemRequest;
import com.example.switching.reconciliation.dto.ReconItemResponse;
import com.example.switching.reconciliation.entity.ReconciliationFileEntity;
import com.example.switching.reconciliation.entity.ReconciliationItemEntity;
import com.example.switching.reconciliation.service.ReconciliationDiscrepancyService;
import com.example.switching.reconciliation.service.ReconciliationFileService;
import com.example.switching.reconciliation.service.ReconciliationMatchingService;

/**
 * Operations API for reconciliation.
 *
 * <pre>
 *  POST /api/operations/reconciliation/files                        — register file
 *  GET  /api/operations/reconciliation/files?date=&amp;status=          — list files
 *  GET  /api/operations/reconciliation/files/{fileRef}              — file detail
 *  POST /api/operations/reconciliation/files/{fileRef}/items        — import + match items
 *  POST /api/operations/reconciliation/files/{fileRef}/rematch      — re-run matching
 *  GET  /api/operations/reconciliation/files/{fileRef}/items        — all items
 *  GET  /api/operations/reconciliation/files/{fileRef}/discrepancies — discrepancy report
 * </pre>
 */
@RestController
@RequestMapping("/api/operations/reconciliation")
public class ReconciliationController {

    private final ReconciliationFileService       fileService;
    private final ReconciliationMatchingService   matchingService;
    private final ReconciliationDiscrepancyService discrepancyService;

    public ReconciliationController(ReconciliationFileService fileService,
                                     ReconciliationMatchingService matchingService,
                                     ReconciliationDiscrepancyService discrepancyService) {
        this.fileService        = fileService;
        this.matchingService    = matchingService;
        this.discrepancyService = discrepancyService;
    }

    // ── File registration ─────────────────────────────────────────────────────

    @PostMapping("/files")
    public ResponseEntity<ReconFileResponse> createFile(@RequestBody CreateReconFileRequest request) {
        ReconciliationFileEntity file = fileService.createFile(request);
        return ResponseEntity.ok(toFileResponse(file));
    }

    // ── List files ────────────────────────────────────────────────────────────

    @GetMapping("/files")
    public ResponseEntity<List<ReconFileResponse>> listFiles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {

        List<ReconciliationFileEntity> files;
        if (date != null) {
            files = fileService.listByDate(date);
        } else {
            files = fileService.listByStatus(status != null ? status.toUpperCase() : "RECEIVED");
        }
        return ResponseEntity.ok(files.stream().map(this::toFileResponse).toList());
    }

    // ── File detail ───────────────────────────────────────────────────────────

    @GetMapping("/files/{fileRef}")
    public ResponseEntity<ReconFileResponse> getFile(@PathVariable String fileRef) {
        return ResponseEntity.ok(toFileResponse(fileService.requireFile(fileRef)));
    }

    // ── Import + match items ──────────────────────────────────────────────────

    @PostMapping("/files/{fileRef}/items")
    public ResponseEntity<List<ReconItemResponse>> importItems(
            @PathVariable String fileRef,
            @RequestBody List<ReconItemRequest> items) {

        List<ReconciliationItemEntity> result = matchingService.importAndMatch(fileRef, items);
        return ResponseEntity.ok(result.stream().map(this::toItemResponse).toList());
    }

    // ── Re-run matching ───────────────────────────────────────────────────────

    @PostMapping("/files/{fileRef}/rematch")
    public ResponseEntity<List<ReconItemResponse>> rematch(@PathVariable String fileRef) {
        List<ReconciliationItemEntity> result = matchingService.rematch(fileRef);
        return ResponseEntity.ok(result.stream().map(this::toItemResponse).toList());
    }

    // ── All items ─────────────────────────────────────────────────────────────

    @GetMapping("/files/{fileRef}/items")
    public ResponseEntity<List<ReconItemResponse>> getAllItems(@PathVariable String fileRef) {
        return ResponseEntity.ok(discrepancyService.getAllItems(fileRef));
    }

    // ── Discrepancy report ────────────────────────────────────────────────────

    @GetMapping("/files/{fileRef}/discrepancies")
    public ResponseEntity<ReconDiscrepancyReport> getDiscrepancies(@PathVariable String fileRef) {
        return ResponseEntity.ok(discrepancyService.getReport(fileRef));
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private ReconFileResponse toFileResponse(ReconciliationFileEntity f) {
        return new ReconFileResponse(
                f.getId(),
                f.getFileRef(),
                f.getSourceBank(),
                f.getFileName(),
                f.getFileType(),
                f.getFileSizeBytes(),
                f.getReconciliationDate(),
                f.getStatus(),
                f.getTotalRecords(),
                f.getMatchedCount(),
                f.getUnmatchedCount(),
                f.getUploadedBy(),
                f.getCreatedAt()
        );
    }

    private ReconItemResponse toItemResponse(ReconciliationItemEntity i) {
        return new ReconItemResponse(
                i.getId(),
                i.getFileId(),
                i.getLineNumber(),
                i.getTransactionRef(),
                i.getExternalRef(),
                i.getAmount(),
                i.getCurrency(),
                i.getMatchStatus(),
                i.getMismatchReason(),
                i.getReconciliationDate(),
                i.getMatchedAt()
        );
    }
}
