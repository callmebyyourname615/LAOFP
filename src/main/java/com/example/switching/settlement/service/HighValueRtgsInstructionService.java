package com.example.switching.settlement.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.repository.SettlementInstructionRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.repository.TransferRepository;

@Service
public class HighValueRtgsInstructionService {

    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_INSTRUCTION";
    private static final String SOURCE_TYPE = "HIGH_VALUE_TRANSFER";

    private final TransferRepository transferRepository;
    private final SettlementInstructionRepository instructionRepository;
    private final AuditLogService auditLogService;

    public HighValueRtgsInstructionService(TransferRepository transferRepository,
                                           SettlementInstructionRepository instructionRepository,
                                           AuditLogService auditLogService) {
        this.transferRepository = transferRepository;
        this.instructionRepository = instructionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SettlementInstructionEntity generatePendingInstruction(String transferRef) {
        return instructionRepository.findByTransferRef(transferRef)
                .orElseGet(() -> createInstruction(requireTransfer(transferRef)));
    }

    private TransferEntity requireTransfer(String transferRef) {
        return transferRepository.findByTransferRef(transferRef)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferRef));
    }

    private SettlementInstructionEntity createInstruction(TransferEntity transfer) {
        requireHighValueRtgsTransfer(transfer);

        SettlementInstructionEntity instruction = new SettlementInstructionEntity();
        instruction.setInstructionRef("HV-" + transfer.getTransferRef());
        instruction.setSourceType(SOURCE_TYPE);
        instruction.setTransferRef(transfer.getTransferRef());
        instruction.setCycleId(null);
        instruction.setDebtorPspId(transfer.getSourceBank());
        instruction.setCreditorPspId(transfer.getDestinationBank());
        instruction.setCurrency(transfer.getCurrency());
        instruction.setNetAmount(transfer.getAmount());
        instruction.setStatus("PENDING_APPROVAL");

        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("HIGH_VALUE_RTGS_INSTRUCTION_GENERATED", ENTITY, saved.getInstructionRef(), SOURCE,
                Map.of(
                        "instructionRef", saved.getInstructionRef(),
                        "transferRef", transfer.getTransferRef(),
                        "debtorPspId", transfer.getSourceBank(),
                        "creditorPspId", transfer.getDestinationBank(),
                        "amount", transfer.getAmount(),
                        "currency", transfer.getCurrency()));
        return saved;
    }

    private void requireHighValueRtgsTransfer(TransferEntity transfer) {
        if (!transfer.isHighValue() || !"RTGS".equalsIgnoreCase(transfer.getSettlementMethod())) {
            throw new IllegalStateException(
                    "Transfer is not routed to high-value RTGS: " + transfer.getTransferRef());
        }
        if (transfer.getAmount() == null || transfer.getAmount().signum() <= 0) {
            throw new IllegalStateException("Transfer amount must be positive: " + transfer.getTransferRef());
        }
        if (transfer.getSourceBank() == null || transfer.getDestinationBank() == null) {
            throw new IllegalStateException("Transfer parties are required: " + transfer.getTransferRef());
        }
    }
}
