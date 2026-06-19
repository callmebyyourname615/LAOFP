package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.repository.SettlementInstructionRepository;
import com.example.switching.settlement.repository.SettlementPositionRepository;

@Service
public class SettlementInstructionService {

    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_INSTRUCTION";

    private final SettlementCycleService cycleService;
    private final SettlementPositionRepository positionRepository;
    private final SettlementInstructionRepository instructionRepository;
    private final AuditLogService auditLogService;

    public SettlementInstructionService(SettlementCycleService cycleService,
                                        SettlementPositionRepository positionRepository,
                                        SettlementInstructionRepository instructionRepository,
                                        AuditLogService auditLogService) {
        this.cycleService = cycleService;
        this.positionRepository = positionRepository;
        this.instructionRepository = instructionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public List<SettlementInstructionEntity> generateForCycle(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        if (!"CLOSED".equals(cycle.getStatus())) {
            throw new IllegalStateException(
                    "Settlement instructions require CLOSED cycle. Current status: "
                    + cycle.getStatus() + " (cycleRef=" + cycleRef + ")");
        }

        if (instructionRepository.existsByCycleId(cycle.getId())) {
            return instructionRepository.findByCycleIdOrderByInstructionRefAsc(cycle.getId());
        }

        List<SettlementPositionEntity> positions =
                positionRepository.findByCycleIdOrderByBankCodeAsc(cycle.getId());
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();

        for (SettlementPositionEntity position : positions) {
            BigDecimal net = position.getNetPosition();
            if (net == null || net.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Balance balance = new Balance(position.getBankCode(), position.getCurrency(), net.abs());
            if (net.signum() < 0) {
                debtors.add(balance);
            } else {
                creditors.add(balance);
            }
        }

        debtors.sort(Comparator.comparing(Balance::bankCode));
        creditors.sort(Comparator.comparing(Balance::bankCode));

        List<SettlementInstructionEntity> created = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        int sequence = 1;

        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Balance debtor = debtors.get(debtorIndex);
            Balance creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.remaining().min(creditor.remaining());

            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                SettlementInstructionEntity instruction = new SettlementInstructionEntity();
                instruction.setInstructionRef("%s-SI%03d".formatted(cycle.getCycleRef(), sequence++));
                instruction.setCycleId(cycle.getId());
                instruction.setDebtorPspId(debtor.bankCode());
                instruction.setCreditorPspId(creditor.bankCode());
                instruction.setCurrency(debtor.currency());
                instruction.setNetAmount(amount);
                instruction.setStatus("PENDING_APPROVAL");
                created.add(instructionRepository.save(instruction));
            }

            debtor.reduce(amount);
            creditor.reduce(amount);
            if (debtor.remaining().compareTo(BigDecimal.ZERO) == 0) {
                debtorIndex++;
            }
            if (creditor.remaining().compareTo(BigDecimal.ZERO) == 0) {
                creditorIndex++;
            }
        }

        auditLogService.log("SETTLEMENT_INSTRUCTIONS_GENERATED", ENTITY, cycleRef, SOURCE,
                Map.of("cycleRef", cycleRef, "instructionCount", created.size()));
        return created;
    }

    @Transactional(readOnly = true)
    public List<SettlementInstructionEntity> listForCycle(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        return instructionRepository.findByCycleIdOrderByInstructionRefAsc(cycle.getId());
    }

    @Transactional
    public SettlementInstructionEntity approve(String instructionRef, String actor, String note) {
        SettlementInstructionEntity instruction = requireInstruction(instructionRef);
        requireStatus(instruction, "PENDING_APPROVAL");
        instruction.setStatus("APPROVED");
        instruction.setApprovedBy(actor);
        instruction.setApprovedAt(LocalDateTime.now());
        instruction.setApprovalNote(note);
        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("SETTLEMENT_INSTRUCTION_APPROVED", ENTITY, instructionRef, actor,
                Map.of("instructionRef", instructionRef, "note", note != null ? note : ""));
        return saved;
    }

    @Transactional
    public SettlementInstructionEntity reject(String instructionRef, String actor, String reason) {
        SettlementInstructionEntity instruction = requireInstruction(instructionRef);
        requireStatus(instruction, "PENDING_APPROVAL");
        instruction.setStatus("REJECTED");
        instruction.setRejectedBy(actor);
        instruction.setRejectedAt(LocalDateTime.now());
        instruction.setRejectionReason(reason);
        SettlementInstructionEntity saved = instructionRepository.save(instruction);
        auditLogService.log("SETTLEMENT_INSTRUCTION_REJECTED", ENTITY, instructionRef, actor,
                Map.of("instructionRef", instructionRef, "reason", reason != null ? reason : ""));
        return saved;
    }

    @Transactional(readOnly = true)
    public SettlementInstructionEntity requireInstruction(String instructionRef) {
        return instructionRepository.findByInstructionRef(instructionRef)
                .orElseThrow(() -> new IllegalArgumentException("Settlement instruction not found: " + instructionRef));
    }

    private void requireStatus(SettlementInstructionEntity instruction, String expected) {
        if (!expected.equals(instruction.getStatus())) {
            throw new IllegalStateException(
                    "Cannot update instruction " + instruction.getInstructionRef()
                    + " from " + instruction.getStatus() + " — expected " + expected);
        }
    }

    private record Balance(String bankCode, String currency, BigDecimal[] remainingHolder) {
        Balance(String bankCode, String currency, BigDecimal remaining) {
            this(bankCode, currency, new BigDecimal[] { remaining });
        }

        BigDecimal remaining() {
            return remainingHolder[0];
        }

        void reduce(BigDecimal amount) {
            remainingHolder[0] = remainingHolder[0].subtract(amount);
        }
    }
}
