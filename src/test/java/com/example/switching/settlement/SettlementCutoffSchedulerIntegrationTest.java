package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.repository.SettlementCycleRepository;
import com.example.switching.settlement.repository.SettlementInstructionRepository;
import com.example.switching.settlement.service.SettlementCutoffScheduler;
import com.example.switching.settlement.service.SettlementDateService;

class SettlementCutoffSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCutoffScheduler cutoffScheduler;
    @Autowired private SettlementCycleRepository cycleRepository;
    @Autowired private SettlementInstructionRepository instructionRepository;
    @Autowired private SettlementDateService settlementDateService;

    /**
     * Purge any settlement state for the two dates this test will use, so the
     * test is deterministic regardless of which other settlement tests ran first
     * in the shared Testcontainers database.
     */
    @BeforeEach
    void cleanSettlementState() {
        LocalDate settlementDate = settlementDateService.nextBusinessDay(LocalDate.now());
        // Remove instructions → positions → items → cycles for this settlement date
        jdbcTemplate.update("""
                DELETE FROM settlement_instructions si
                 USING settlement_cycles sc
                 WHERE si.cycle_id = sc.id
                   AND sc.settlement_date = ?
                """, settlementDate);
        jdbcTemplate.update("""
                DELETE FROM settlement_positions sp
                 USING settlement_cycles sc
                 WHERE sp.cycle_id = sc.id
                   AND sc.settlement_date = ?
                """, settlementDate);
        jdbcTemplate.update("""
                DELETE FROM settlement_items si
                 USING settlement_cycles sc
                 WHERE si.cycle_id = sc.id
                   AND sc.settlement_date = ?
                """, settlementDate);
        jdbcTemplate.update(
                "DELETE FROM settlement_cycles WHERE settlement_date = ?", settlementDate);
    }

    @Test
    void runCutoffCycle_opensBatchesClosesAndGeneratesInstructions() {
        LocalDate settlementDate = settlementDateService.nextBusinessDay(LocalDate.now());
        LocalDate businessDate = settlementDateService.previousBusinessDay(settlementDate);
        seedSettledTransfer("CUT-DNS-A-" + System.nanoTime(), businessDate, "BANK_A", "BANK_B", "1000.00");
        seedSettledTransfer("CUT-DNS-B-" + System.nanoTime(), businessDate, "BANK_C", "BANK_B", "500.00");

        SettlementCutoffScheduler.CutoffRunResult result = cutoffScheduler.runCutoffCycle(1);

        assertTrue(result.executed());
        assertTrue(result.transferCount() >= 2,
                "Expected at least 2 transfers batched, got " + result.transferCount());
        // ≥2 instructions because accumulated transfers from the shared-DB test suite may
        // create extra net-position entries; the important invariant is that the cycle ran
        // and produced meaningful output.
        assertTrue(result.instructionCount() >= 2,
                "Expected at least 2 instructions, got " + result.instructionCount());

        SettlementCycleEntity cycle = cycleRepository.findByCycleRef(result.cycleRef()).orElseThrow();
        assertEquals(settlementDate, cycle.getSettlementDate());
        assertEquals("CLOSED", cycle.getStatus());
        assertEquals(result.transferCount() * 2, countSettlementItems(cycle.getId()));
        assertTrue(instructionRepository.findByCycleIdOrderByInstructionRefAsc(cycle.getId()).size() >= 2,
                "Expected at least 2 instructions for cycle " + cycle.getCycleRef());
    }

    @Test
    void runCutoffCycle_skipsWhenLockIsHeld() {
        assertTrue(jdbcTemplate.queryForList("""
                INSERT INTO scheduler_locks (lock_name, lock_until, locked_at, locked_by)
                VALUES ('SETTLEMENT_CUTOFF_C4', ?, ?, 'test-holder')
                ON CONFLICT (lock_name) DO UPDATE
                SET lock_until = EXCLUDED.lock_until,
                    locked_at = EXCLUDED.locked_at,
                    locked_by = EXCLUDED.locked_by
                RETURNING 1
                """, Integer.class, LocalDateTime.now().plusMinutes(10), LocalDateTime.now()).size() == 1);

        SettlementCutoffScheduler.CutoffRunResult result = cutoffScheduler.runCutoffCycle(4);

        assertFalse(result.executed());
        assertEquals(0, result.transferCount());
        assertEquals(0, result.instructionCount());
    }

    private int countSettlementItems(Long cycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_items WHERE cycle_id = ?",
                Integer.class,
                cycleId);
    }

    private void seedSettledTransfer(String transferRef, LocalDate businessDate,
                                     String sourceBank, String destinationBank, String amount) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, '010100000001', ?, '020200000001',
                    'Receiver', ?, 'LAK', 'API', 'ROUTE_CUTOFF', 'MOCK_BANK_CONNECTOR',
                    'SETTLED', ?, ?, 'DNS', false, ?, ?, ?, ?)
                """,
                transferRef,
                transferRef,
                transferRef,
                transferRef,
                "INQ-" + transferRef,
                sourceBank,
                destinationBank,
                new BigDecimal(amount),
                "EXT-" + transferRef,
                "REF-" + transferRef,
                businessDate,
                now,
                now,
                now);
    }
}
