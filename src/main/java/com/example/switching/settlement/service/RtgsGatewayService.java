package com.example.switching.settlement.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.iso.mapper.Pacs009XmlBuilder;
import com.example.switching.settlement.dto.RtgsCallbackRequest;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.repository.SettlementInstructionRepository;

@Service
public class RtgsGatewayService {

    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_INSTRUCTION";

    private final SettlementInstructionService instructionService;
    private final SettlementInstructionRepository instructionRepository;
    private final Pacs009XmlBuilder pacs009XmlBuilder;
    private final AuditLogService auditLogService;
    private final SettlementCycleService cycleService;
    private final SettlementNetPositionService netPositionService;
    private final Camt054ReportService reportService;
    private final HttpClient httpClient;
    private final String bolRtgsUrl;
    private final Duration requestTimeout;

    public RtgsGatewayService(SettlementInstructionService instructionService,
                              SettlementInstructionRepository instructionRepository,
                              Pacs009XmlBuilder pacs009XmlBuilder,
                              AuditLogService auditLogService,
                              SettlementCycleService cycleService,
                              SettlementNetPositionService netPositionService,
                              Camt054ReportService reportService,
                              @Value("${switching.settlement.bol-rtgs-url}") String bolRtgsUrl,
                              @Value("${switching.settlement.rtgs-timeout-ms}") long timeoutMs) {
        this.instructionService = instructionService;
        this.instructionRepository = instructionRepository;
        this.pacs009XmlBuilder = pacs009XmlBuilder;
        this.auditLogService = auditLogService;
        this.cycleService = cycleService;
        this.netPositionService = netPositionService;
        this.reportService = reportService;
        this.bolRtgsUrl = bolRtgsUrl;
        this.requestTimeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();
    }

    @Transactional
    public SettlementInstructionEntity prepareManualRtgsFile(String instructionRef, String actor) {
        SettlementInstructionEntity instruction = instructionService.requireInstruction(instructionRef);
        if (!"APPROVED".equals(instruction.getStatus()) && !"SENT_RTGS".equals(instruction.getStatus())) {
            throw new IllegalStateException(
                    "Cannot prepare RTGS file for instruction " + instructionRef
                    + " from " + instruction.getStatus() + " — expected APPROVED or SENT_RTGS");
        }
        ensureRtgsPayload(instruction);
        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("SETTLEMENT_INSTRUCTION_RTGS_FILE_PREPARED", ENTITY, instructionRef, actorOrSource(actor),
                Map.of("instructionRef", instructionRef,
                        "rtgsMsgId", saved.getRtgsMsgId()));
        return saved;
    }

    @Transactional
    public SettlementInstructionEntity recordManualRtgsUpload(String instructionRef, String actor, String note) {
        SettlementInstructionEntity instruction = instructionService.requireInstruction(instructionRef);
        if ("SENT_RTGS".equals(instruction.getStatus()) || "CONFIRMED".equals(instruction.getStatus())) {
            return instruction;
        }
        requireStatus(instruction, "APPROVED");
        ensureRtgsPayload(instruction);
        instruction.setStatus("SENT_RTGS");
        instruction.setSentAt(LocalDateTime.now());
        instruction.setRtgsResponsePayload(note);
        instruction.setLastError(null);
        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("SETTLEMENT_INSTRUCTION_RTGS_PORTAL_UPLOADED", ENTITY, instructionRef, actorOrSource(actor),
                Map.of("instructionRef", instructionRef,
                        "rtgsMsgId", saved.getRtgsMsgId(),
                        "note", note != null ? note : ""));
        return saved;
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public SettlementInstructionEntity sendApprovedInstruction(String instructionRef, String actor) {
        SettlementInstructionEntity instruction = instructionService.requireInstruction(instructionRef);
        requireStatus(instruction, "APPROVED");

        String rtgsMsgId = instruction.getRtgsMsgId();
        if (rtgsMsgId == null || rtgsMsgId.isBlank()) {
            rtgsMsgId = "RTGS-" + instructionRef + "-" + System.currentTimeMillis();
        }

        String requestPayload = pacs009XmlBuilder.build(instruction, rtgsMsgId);
        instruction.setRtgsMsgId(rtgsMsgId);
        instruction.setRtgsRequestPayload(requestPayload);

        try {
            HttpResponse<String> response = httpClient.send(request(requestPayload, instructionRef, rtgsMsgId),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            instruction.setRtgsResponsePayload(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "RTGS submission failed with HTTP " + response.statusCode();
                instruction.setLastError(message);
                instructionRepository.save(instruction);
                auditLogService.log("SETTLEMENT_INSTRUCTION_RTGS_FAILED", ENTITY, instructionRef, actorOrSource(actor),
                        Map.of("instructionRef", instructionRef, "rtgsMsgId", rtgsMsgId,
                                "statusCode", response.statusCode()));
                throw new IllegalStateException(message);
            }

            instruction.setStatus("SENT_RTGS");
            instruction.setSentAt(LocalDateTime.now());
            instruction.setLastError(null);
            SettlementInstructionEntity saved = instructionRepository.save(instruction);
            auditLogService.log("SETTLEMENT_INSTRUCTION_SENT_RTGS", ENTITY, instructionRef, actorOrSource(actor),
                    Map.of("instructionRef", instructionRef, "rtgsMsgId", rtgsMsgId,
                            "statusCode", response.statusCode()));
            return saved;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = "RTGS submission error: " + ex.getMessage();
            instruction.setLastError(message);
            instructionRepository.save(instruction);
            auditLogService.log("SETTLEMENT_INSTRUCTION_RTGS_ERROR", ENTITY, instructionRef, actorOrSource(actor),
                    Map.of("instructionRef", instructionRef, "rtgsMsgId", rtgsMsgId, "error", message));
            throw new IllegalStateException(message, ex);
        }
    }

    @Transactional
    public SettlementInstructionEntity applyRtgsCallback(RtgsCallbackRequest request, String sourceIp) {
        if (request == null) {
            throw new IllegalArgumentException("RTGS callback request is required");
        }
        SettlementInstructionEntity instruction = findCallbackInstruction(request);
        String callbackStatus = normalizeStatus(request.status());

        if ("CONFIRMED".equals(instruction.getStatus()) && isAcceptedCallback(callbackStatus)) {
            return instruction;
        }
        if ("FAILED".equals(instruction.getStatus()) && isFailedCallback(callbackStatus)) {
            return instruction;
        }
        if (!"SENT_RTGS".equals(instruction.getStatus())) {
            throw new IllegalStateException(
                    "Cannot apply RTGS callback to instruction " + instruction.getInstructionRef()
                    + " from " + instruction.getStatus() + " — expected SENT_RTGS");
        }

        if (isAcceptedCallback(callbackStatus)) {
            instruction.setStatus("CONFIRMED");
            instruction.setConfirmedAt(LocalDateTime.now());
            instruction.setLastError(null);
        } else if (isFailedCallback(callbackStatus)) {
            instruction.setStatus("FAILED");
            instruction.setLastError(request.reason() != null && !request.reason().isBlank()
                    ? request.reason()
                    : "RTGS callback status: " + callbackStatus);
        } else {
            throw new IllegalArgumentException("Unsupported RTGS callback status: " + request.status());
        }

        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("SETTLEMENT_INSTRUCTION_RTGS_CALLBACK", ENTITY, saved.getInstructionRef(), SOURCE,
                Map.of("instructionRef", saved.getInstructionRef(),
                        "rtgsMsgId", saved.getRtgsMsgId() != null ? saved.getRtgsMsgId() : "",
                        "callbackStatus", callbackStatus,
                        "sourceIp", sourceIp != null ? sourceIp : ""));
        settleCycleWhenAllInstructionsConfirmed(saved);
        return saved;
    }

    private void settleCycleWhenAllInstructionsConfirmed(SettlementInstructionEntity instruction) {
        Long cycleId = instruction.getCycleId();
        if (cycleId == null || !instructionService.allInstructionsConfirmed(cycleId)) {
            return;
        }
        var cycle = cycleService.requireCycleById(cycleId);
        if ("SETTLED".equals(cycle.getStatus())) {
            return;
        }
        netPositionService.settle(cycle.getCycleRef());
        reportService.generateReportsForCycle(cycle.getCycleRef());
    }

    private void ensureRtgsPayload(SettlementInstructionEntity instruction) {
        String rtgsMsgId = instruction.getRtgsMsgId();
        if (rtgsMsgId == null || rtgsMsgId.isBlank()) {
            rtgsMsgId = "RTGS-" + instruction.getInstructionRef() + "-" + System.currentTimeMillis();
        }
        instruction.setRtgsMsgId(rtgsMsgId);
        if (instruction.getRtgsRequestPayload() == null || instruction.getRtgsRequestPayload().isBlank()) {
            instruction.setRtgsRequestPayload(pacs009XmlBuilder.build(instruction, rtgsMsgId));
        }
    }

    private HttpRequest request(String requestPayload, String instructionRef, String rtgsMsgId) {
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(bolRtgsUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/xml")
                    .header("X-Instruction-Ref", instructionRef)
                    .header("X-RTGS-Message-Id", rtgsMsgId)
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid RTGS endpoint URL: " + bolRtgsUrl, ex);
        }
    }

    private SettlementInstructionEntity findCallbackInstruction(RtgsCallbackRequest request) {
        if (request.instructionRef() != null && !request.instructionRef().isBlank()) {
            return instructionRepository.findByInstructionRef(request.instructionRef())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Settlement instruction not found: " + request.instructionRef()));
        }
        if (request.rtgsMsgId() != null && !request.rtgsMsgId().isBlank()) {
            return instructionRepository.findByRtgsMsgId(request.rtgsMsgId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Settlement instruction not found for rtgsMsgId: " + request.rtgsMsgId()));
        }
        throw new IllegalArgumentException("RTGS callback requires instructionRef or rtgsMsgId");
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("RTGS callback status is required");
        }
        return status.trim().toUpperCase();
    }

    private boolean isAcceptedCallback(String status) {
        return "ACCEPTED".equals(status) || "CONFIRMED".equals(status) || "SETTLED".equals(status);
    }

    private boolean isFailedCallback(String status) {
        return "REJECTED".equals(status) || "FAILED".equals(status) || "ERROR".equals(status);
    }

    private void requireStatus(SettlementInstructionEntity instruction, String expected) {
        if (!expected.equals(instruction.getStatus())) {
            throw new IllegalStateException(
                    "Cannot send instruction " + instruction.getInstructionRef()
                    + " from " + instruction.getStatus() + " — expected " + expected);
        }
    }

    private String actorOrSource(String actor) {
        return actor != null && !actor.isBlank() ? actor : SOURCE;
    }
}
