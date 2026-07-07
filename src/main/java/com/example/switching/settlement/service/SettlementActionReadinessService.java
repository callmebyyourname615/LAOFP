package com.example.switching.settlement.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.operations.dto.OperationActionDecisionResponse;
import com.example.switching.settlement.dto.SettlementCycleActionsResponse;
import com.example.switching.settlement.dto.SettlementInstructionActionsResponse;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;

@Service
public class SettlementActionReadinessService {

    private final SettlementCycleService cycleService;
    private final SettlementBatchService batchService;
    private final SettlementInstructionService instructionService;

    public SettlementActionReadinessService(
            SettlementCycleService cycleService,
            SettlementBatchService batchService,
            SettlementInstructionService instructionService) {
        this.cycleService = cycleService;
        this.batchService = batchService;
        this.instructionService = instructionService;
    }

    @Transactional(readOnly = true)
    public SettlementCycleActionsResponse actions(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        int itemCount = batchService.countItems(cycle.getId());
        List<SettlementInstructionEntity> instructions = instructionService.listForCycle(cycleRef);

        Map<String, OperationActionDecisionResponse> actions = new LinkedHashMap<>();
        actions.put("canBatch", canBatch(cycle));
        actions.put("canClose", canClose(cycle));
        actions.put("canGenerateInstructions", canGenerateInstructions(cycle, itemCount, instructions));
        actions.put("canSettle", canSettle(cycle, instructions));
        actions.put("canDownloadOpsReport", canDownloadOpsReport(cycle));
        actions.put("canDownloadSettlementReports", canDownloadSettlementReports(cycle));

        List<SettlementInstructionActionsResponse> instructionActions = instructions.stream()
                .map(this::instructionActions)
                .toList();

        return new SettlementCycleActionsResponse(
                LocalDateTime.now(),
                cycleRef,
                cycle.getStatus(),
                itemCount,
                instructions.size(),
                actions,
                instructionActions);
    }

    private OperationActionDecisionResponse canBatch(SettlementCycleEntity cycle) {
        if ("OPEN".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.allowed("Cycle is OPEN and can accept settlement batch items");
        }
        return OperationActionDecisionResponse.denied(
                "Cycle status is " + cycle.getStatus() + "; batching requires OPEN");
    }

    private OperationActionDecisionResponse canClose(SettlementCycleEntity cycle) {
        if ("OPEN".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.allowed("Cycle is OPEN and can be closed");
        }
        return OperationActionDecisionResponse.denied(
                "Cycle status is " + cycle.getStatus() + "; close requires OPEN");
    }

    private OperationActionDecisionResponse canGenerateInstructions(
            SettlementCycleEntity cycle,
            int itemCount,
            List<SettlementInstructionEntity> instructions) {
        if (!"CLOSED".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.denied(
                    "Cycle status is " + cycle.getStatus() + "; instruction generation requires CLOSED");
        }
        if (itemCount <= 0) {
            return OperationActionDecisionResponse.denied("Cycle has no settlement items");
        }
        if (!instructions.isEmpty()) {
            return OperationActionDecisionResponse.denied("Settlement instructions already exist for this cycle");
        }
        return OperationActionDecisionResponse.allowed("Cycle is CLOSED with settlement items and no instructions yet");
    }

    private OperationActionDecisionResponse canSettle(
            SettlementCycleEntity cycle,
            List<SettlementInstructionEntity> instructions) {
        if ("SETTLED".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.denied("Cycle is already SETTLED");
        }
        if (!"CLOSED".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.denied(
                    "Cycle status is " + cycle.getStatus() + "; settlement requires CLOSED");
        }
        if (instructions.isEmpty()) {
            return OperationActionDecisionResponse.denied("No settlement instructions generated");
        }
        List<String> notConfirmed = instructions.stream()
                .filter(i -> !"CONFIRMED".equals(i.getStatus()))
                .map(i -> i.getInstructionRef() + "=" + i.getStatus())
                .toList();
        if (!notConfirmed.isEmpty()) {
            return OperationActionDecisionResponse.denied(
                    "All instructions must be CONFIRMED before settlement; pending: " + String.join(", ", notConfirmed));
        }
        return OperationActionDecisionResponse.allowed("Cycle is CLOSED and all settlement instructions are CONFIRMED");
    }

    private OperationActionDecisionResponse canDownloadOpsReport(SettlementCycleEntity cycle) {
        if ("SETTLED".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.allowed("Cycle is SETTLED; operations report is available");
        }
        return OperationActionDecisionResponse.denied(
                "Operations report is available only after SETTLED; current status is " + cycle.getStatus());
    }

    private OperationActionDecisionResponse canDownloadSettlementReports(SettlementCycleEntity cycle) {
        if ("SETTLED".equals(cycle.getStatus())) {
            return OperationActionDecisionResponse.allowed("Cycle is SETTLED; bank-facing settlement reports can be downloaded");
        }
        return OperationActionDecisionResponse.denied(
                "Settlement reports are generated after SETTLED; current status is " + cycle.getStatus());
    }

    private SettlementInstructionActionsResponse instructionActions(SettlementInstructionEntity instruction) {
        Map<String, OperationActionDecisionResponse> actions = new LinkedHashMap<>();
        actions.put("canApprove", canApproveInstruction(instruction));
        actions.put("canReject", canRejectInstruction(instruction));
        actions.put("canExportRtgsFile", canExportRtgsFile(instruction));
        actions.put("canRecordRtgsUpload", canRecordRtgsUpload(instruction));
        actions.put("canSendRtgs", canRecordRtgsUpload(instruction));

        return new SettlementInstructionActionsResponse(
                instruction.getInstructionRef(),
                instruction.getStatus(),
                actions);
    }

    private OperationActionDecisionResponse canApproveInstruction(SettlementInstructionEntity instruction) {
        if ("PENDING_APPROVAL".equals(instruction.getStatus())) {
            return OperationActionDecisionResponse.allowed("Instruction is PENDING_APPROVAL");
        }
        return OperationActionDecisionResponse.denied(
                "Instruction status is " + instruction.getStatus() + "; approve requires PENDING_APPROVAL");
    }

    private OperationActionDecisionResponse canRejectInstruction(SettlementInstructionEntity instruction) {
        if ("PENDING_APPROVAL".equals(instruction.getStatus())) {
            return OperationActionDecisionResponse.allowed("Instruction is PENDING_APPROVAL");
        }
        return OperationActionDecisionResponse.denied(
                "Instruction status is " + instruction.getStatus() + "; reject requires PENDING_APPROVAL");
    }

    private OperationActionDecisionResponse canExportRtgsFile(SettlementInstructionEntity instruction) {
        if ("APPROVED".equals(instruction.getStatus()) || "SENT_RTGS".equals(instruction.getStatus())) {
            return OperationActionDecisionResponse.allowed("Instruction can prepare or re-download PACS.009");
        }
        return OperationActionDecisionResponse.denied(
                "Instruction status is " + instruction.getStatus() + "; PACS.009 export requires APPROVED or SENT_RTGS");
    }

    private OperationActionDecisionResponse canRecordRtgsUpload(SettlementInstructionEntity instruction) {
        if ("APPROVED".equals(instruction.getStatus())) {
            return OperationActionDecisionResponse.allowed("Instruction is APPROVED and can be marked as uploaded to STGS/RTGS");
        }
        return OperationActionDecisionResponse.denied(
                "Instruction status is " + instruction.getStatus() + "; upload recording requires APPROVED");
    }
}
