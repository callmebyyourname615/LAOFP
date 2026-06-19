package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementInstructionService;

class SettlementInstructionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private SettlementInstructionService instructionService;

    @Test
    void generateForCycle_closedCycleCreatesPendingApprovalInstructionsAndIsIdempotent() {
        SettlementCycleEntity cycle = closedCycleWithPositions(
                LocalDate.now().plusDays(120),
                "BANK_A", new BigDecimal("1000.00"), BigDecimal.ZERO,
                "BANK_B", BigDecimal.ZERO, new BigDecimal("1000.00"));

        List<SettlementInstructionEntity> generated =
                instructionService.generateForCycle(cycle.getCycleRef());

        assertEquals(1, generated.size());
        SettlementInstructionEntity instruction = generated.getFirst();
        assertTrue(instruction.getInstructionRef().startsWith(cycle.getCycleRef() + "-SI"));
        assertEquals(cycle.getId(), instruction.getCycleId());
        assertEquals("BANK_A", instruction.getDebtorPspId());
        assertEquals("BANK_B", instruction.getCreditorPspId());
        assertEquals("LAK", instruction.getCurrency());
        assertEquals(0, new BigDecimal("1000.00").compareTo(instruction.getNetAmount()));
        assertEquals("PENDING_APPROVAL", instruction.getStatus());

        List<SettlementInstructionEntity> regenerated =
                instructionService.generateForCycle(cycle.getCycleRef());
        assertEquals(1, regenerated.size());
        assertEquals(instruction.getInstructionRef(), regenerated.getFirst().getInstructionRef());
    }

    @Test
    void approveAndReject_requirePendingApprovalState() {
        SettlementCycleEntity approveCycle = closedCycleWithPositions(
                LocalDate.now().plusDays(121),
                "BANK_A", new BigDecimal("700.00"), BigDecimal.ZERO,
                "BANK_B", BigDecimal.ZERO, new BigDecimal("700.00"));
        SettlementInstructionEntity toApprove =
                instructionService.generateForCycle(approveCycle.getCycleRef()).getFirst();

        SettlementInstructionEntity approved =
                instructionService.approve(toApprove.getInstructionRef(), "ops-user", "checked net positions");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals("ops-user", approved.getApprovedBy());
        assertEquals("checked net positions", approved.getApprovalNote());
        assertNotNull(approved.getApprovedAt());
        assertThrows(IllegalStateException.class,
                () -> instructionService.reject(approved.getInstructionRef(), "ops-user", "too late"));

        SettlementCycleEntity rejectCycle = closedCycleWithPositions(
                LocalDate.now().plusDays(122),
                "BANK_C", new BigDecimal("300.00"), BigDecimal.ZERO,
                "BANK_B", BigDecimal.ZERO, new BigDecimal("300.00"));
        SettlementInstructionEntity toReject =
                instructionService.generateForCycle(rejectCycle.getCycleRef()).getFirst();

        SettlementInstructionEntity rejected =
                instructionService.reject(toReject.getInstructionRef(), "checker-user", "reconciliation mismatch");

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("checker-user", rejected.getRejectedBy());
        assertEquals("reconciliation mismatch", rejected.getRejectionReason());
        assertNotNull(rejected.getRejectedAt());
        assertThrows(IllegalStateException.class,
                () -> instructionService.approve(rejected.getInstructionRef(), "checker-user", "too late"));
    }

    @Test
    void generateForCycle_openCycleThrows() {
        SettlementCycleEntity cycle = cycleService.openCycle(LocalDate.now().plusDays(123));

        assertThrows(IllegalStateException.class,
                () -> instructionService.generateForCycle(cycle.getCycleRef()));
    }

    private SettlementCycleEntity closedCycleWithPositions(
            LocalDate settlementDate,
            String debitBank,
            BigDecimal debitAmount,
            BigDecimal debitCreditAmount,
            String creditBank,
            BigDecimal creditDebitAmount,
            BigDecimal creditAmount) {
        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        jdbcTemplate.update("""
                INSERT INTO settlement_positions
                    (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count)
                VALUES (?, ?, 'LAK', ?, ?, 1)
                """, cycle.getId(), debitBank, debitAmount, debitCreditAmount);
        jdbcTemplate.update("""
                INSERT INTO settlement_positions
                    (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count)
                VALUES (?, ?, 'LAK', ?, ?, 1)
                """, cycle.getId(), creditBank, creditDebitAmount, creditAmount);
        return cycleService.closeCycle(cycle.getCycleRef());
    }
}
