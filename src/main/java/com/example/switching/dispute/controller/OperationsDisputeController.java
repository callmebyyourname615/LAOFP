package com.example.switching.dispute.controller;

import jakarta.validation.Valid;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.dto.DisputeResponse;
import com.example.switching.dispute.dto.DrsActionsResponse;
import com.example.switching.dispute.dto.DrsDashboardSummaryResponse;
import com.example.switching.dispute.dto.DrsDisputeDetailResponse;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentDownload;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentListResponse;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentResponse;
import com.example.switching.dispute.dto.DrsDisputeListResponse;
import com.example.switching.dispute.dto.DrsEvidenceReportResponse;
import com.example.switching.dispute.dto.DrsResolutionDecisionRequest;
import com.example.switching.dispute.dto.DrsResolutionSubmitRequest;
import com.example.switching.dispute.dto.DrsTimelineResponse;
import com.example.switching.dispute.dto.PostSettlementDisputeRequest;
import com.example.switching.dispute.service.DrsEvidenceAttachmentService;
import com.example.switching.dispute.service.DrsActionReadinessService;
import com.example.switching.dispute.service.DrsOperationsQueryService;
import com.example.switching.dispute.service.DrsResolutionMakerCheckerService;
import com.example.switching.dispute.service.PostSettlementDisputeService;

@RestController
@RequestMapping("/api/operations/disputes")
public class OperationsDisputeController {

    private final PostSettlementDisputeService postSettlementDisputeService;
    private final DrsResolutionMakerCheckerService makerCheckerService;
    private final DrsOperationsQueryService queryService;
    private final DrsEvidenceAttachmentService attachmentService;
    private final DrsActionReadinessService actionReadinessService;

    public OperationsDisputeController(
            PostSettlementDisputeService postSettlementDisputeService,
            DrsResolutionMakerCheckerService makerCheckerService,
            DrsOperationsQueryService queryService,
            DrsEvidenceAttachmentService attachmentService,
            DrsActionReadinessService actionReadinessService) {
        this.postSettlementDisputeService = postSettlementDisputeService;
        this.makerCheckerService = makerCheckerService;
        this.queryService = queryService;
        this.attachmentService = attachmentService;
        this.actionReadinessService = actionReadinessService;
    }

    @GetMapping
    public ResponseEntity<DrsDisputeListResponse> listDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String disputeType,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(queryService.list(status, bankCode, disputeType, dateFrom, dateTo, limit));
    }

    @GetMapping("/dashboard-summary")
    public ResponseEntity<DrsDashboardSummaryResponse> dashboardSummary(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(queryService.dashboardSummary(limit));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DrsDisputeDetailResponse> disputeDetail(@PathVariable Long disputeId) {
        return ResponseEntity.ok(queryService.detail(disputeId));
    }

    @GetMapping("/{disputeId}/timeline")
    public ResponseEntity<DrsTimelineResponse> disputeTimeline(@PathVariable Long disputeId) {
        return ResponseEntity.ok(queryService.timeline(disputeId));
    }

    @GetMapping("/{disputeId}/actions")
    public ResponseEntity<DrsActionsResponse> disputeActions(@PathVariable Long disputeId) {
        return ResponseEntity.ok(actionReadinessService.actions(disputeId));
    }

    @GetMapping("/{disputeId}/evidence-report")
    public ResponseEntity<DrsEvidenceReportResponse> evidenceReport(@PathVariable Long disputeId) {
        return ResponseEntity.ok(queryService.evidenceReport(disputeId));
    }

    @GetMapping(value = "/{disputeId}/evidence-report.csv", produces = "text/csv")
    public ResponseEntity<String> evidenceReportCsv(@PathVariable Long disputeId) {
        String csv = queryService.evidenceReportCsv(disputeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"DRS-" + disputeId + "-evidence-report.csv\"")
                .body(csv);
    }

    @GetMapping("/{disputeId}/attachments")
    public ResponseEntity<DrsEvidenceAttachmentListResponse> listAttachments(@PathVariable Long disputeId) {
        return ResponseEntity.ok(attachmentService.list(disputeId));
    }

    @PostMapping(value = "/{disputeId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DrsEvidenceAttachmentResponse> uploadAttachment(
            @PathVariable Long disputeId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            Authentication authentication) {
        return ResponseEntity.ok(attachmentService.upload(disputeId, file, description, actor(authentication)));
    }

    @GetMapping("/{disputeId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long disputeId,
            @PathVariable Long attachmentId) {
        DrsEvidenceAttachmentDownload attachment = attachmentService.download(disputeId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.fileSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.fileName())
                        .build()
                        .toString())
                .header("X-Content-SHA256", attachment.sha256())
                .body(attachment.payload());
    }

    @PostMapping("/post-settlement")
    public ResponseEntity<DisputeRaiseResponse> raisePostSettlementDispute(
            @Valid @RequestBody PostSettlementDisputeRequest request,
            Authentication authentication) {
        String actor = authentication != null && authentication.getName() != null
                ? authentication.getName()
                : "OPS";
        return ResponseEntity.ok(postSettlementDisputeService.raise(request, actor));
    }

    @PostMapping("/{disputeId}/submit-resolution")
    public ResponseEntity<DisputeResponse> submitResolution(
            @PathVariable Long disputeId,
            @Valid @RequestBody DrsResolutionSubmitRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(makerCheckerService.submitResolution(disputeId, request, actor(authentication)));
    }

    @PostMapping("/{disputeId}/approve-resolution")
    public ResponseEntity<DisputeResponse> approveResolution(
            @PathVariable Long disputeId,
            @RequestBody(required = false) DrsResolutionDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(makerCheckerService.approveResolution(
                disputeId,
                actor(authentication),
                request != null ? request.note() : null));
    }

    @PostMapping("/{disputeId}/reject-resolution")
    public ResponseEntity<DisputeResponse> rejectResolution(
            @PathVariable Long disputeId,
            @RequestBody(required = false) DrsResolutionDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(makerCheckerService.rejectResolution(
                disputeId,
                actor(authentication),
                request != null ? request.note() : null));
    }

    @PostMapping("/{disputeId}/refund/retry")
    public ResponseEntity<DisputeResponse> retryRefund(
            @PathVariable Long disputeId,
            @RequestBody(required = false) DrsResolutionDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(makerCheckerService.retryRefund(
                disputeId,
                actor(authentication),
                request != null ? request.note() : null));
    }

    private String actor(Authentication authentication) {
        return authentication != null && authentication.getName() != null
                ? authentication.getName()
                : "OPS";
    }
}
