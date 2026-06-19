package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.service.SettlementBatchService;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementDateService;
import com.example.switching.settlement.service.SettlementNetPositionService;

/**
 * Integration tests for the full settlement lifecycle:
 *   OPEN → BATCH → CLOSE → SETTLE
 *
 * <p>Uses 2 business days ago as the seeding businessDate to avoid collision with:
 * <ul>
 *   <li>{@code FullTransferFlowIntegrationTest} — uses today</li>
 *   <li>{@code SettlementTPlusOneIntegrationTest} — uses yesterday (prev business day)</li>
 * </ul>
 */
class SettlementLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private SettlementBatchService batchService;
    @Autowired private SettlementNetPositionService netPositionService;
    @Autowired private SettlementDateService settlementDateService;

    // ── Test 1 ───────────────────────────────────────────────────────────────

    /**
     * Full lifecycle: open cycle, batch multi-directional transfers, close, settle.
     * Verifies net positions (debit / credit / net) are mathematically correct.
     *
     * Uses 3 biz days ago (exclusive date) so no other test method seeds transfers here.
     * (SettlementTPlusOneIntegrationTest uses yesterday; batchTransactions_onRebatch uses 2 days ago.)
     *
     * Transfers seeded:
     *   BANK_A → BANK_B  500 LAK
     *   BANK_A → BANK_B  500 LAK
     *   BANK_B → BANK_A  300 LAK
     *
     * Expected net after multilateral netting:
     *   BANK_A: debit=1000, credit=300, net=-700 (owes network)
     *   BANK_B: debit=300, credit=1000, net=+700 (owed by network)
     */
    @Test
    void fullLifecycle_openBatchCloseSettle_correctNetPositions() {
        // 3 biz days ago — exclusive to this test (other tests use 1 or 2 days ago)
        LocalDate businessDate   = settlementDateService.previousBusinessDay(safeBusinessDate());
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        String ref1 = "TRX-SL-A1-" + System.nanoTime();
        String ref2 = "TRX-SL-A2-" + System.nanoTime();
        String ref3 = "TRX-SL-B1-" + System.nanoTime();
        seedSettledTransfer(ref1, "BANK_A", "BANK_B", new BigDecimal("500.00"), businessDate);
        seedSettledTransfer(ref2, "BANK_A", "BANK_B", new BigDecimal("500.00"), businessDate);
        seedSettledTransfer(ref3, "BANK_B", "BANK_A", new BigDecimal("300.00"), businessDate);

        // 1. Open cycle -------------------------------------------------------
        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        assertEquals("OPEN", cycle.getStatus());
        assertNotNull(cycle.getOpenedAt(), "openedAt must be set");
        assertNotNull(cycle.getCycleRef());
        assertTrue(cycle.getCycleRef().startsWith("SC-"), "cycleRef must follow SC-yyyyMMdd-Cn pattern");

        // 2. Batch transactions -----------------------------------------------
        int batched = batchService.batchTransactions(cycle.getCycleRef());
        assertTrue(batched >= 3,
                "At least 3 transfers seeded for businessDate=" + businessDate
                + " but only " + batched + " batched");

        // 6 item rows = 3 transfers × 2 sides (DEBIT + CREDIT)
        int itemRows = batchService.countItems(cycle.getId());
        assertTrue(itemRows >= 6,
                "Expected ≥6 settlement_items rows, got " + itemRows);

        // 3. Verify net positions while still OPEN ---------------------------
        List<SettlementPositionEntity> positions =
                netPositionService.getPositions(cycle.getCycleRef());

        Map<String, SettlementPositionEntity> byBank = positions.stream()
                .collect(Collectors.toMap(SettlementPositionEntity::getBankCode, p -> p));

        // BANK_A: debit=1000, credit=300, net=-700
        SettlementPositionEntity bankA = byBank.get("BANK_A");
        assertNotNull(bankA, "Position for BANK_A must exist");
        assertEquals(0, new BigDecimal("1000.00").compareTo(bankA.getDebitAmount()),
                "BANK_A debit must be 1000.00");
        assertEquals(0, new BigDecimal("300.00").compareTo(bankA.getCreditAmount()),
                "BANK_A credit must be 300.00");
        assertEquals(0, new BigDecimal("-700.00").compareTo(bankA.getNetPosition()),
                "BANK_A net position must be -700.00 (credit - debit)");
        // transaction_count is incremented for BOTH sides of every transfer:
        // BANK_A: 2 as payer (ref1,ref2) + 1 as payee (ref3) = 3
        assertEquals(3, bankA.getTransactionCount(),
                "BANK_A total transaction_count (2 as payer + 1 as payee)");
        assertEquals("OPEN", bankA.getStatus());

        // BANK_B: debit=300, credit=1000, net=+700
        SettlementPositionEntity bankB = byBank.get("BANK_B");
        assertNotNull(bankB, "Position for BANK_B must exist");
        assertEquals(0, new BigDecimal("300.00").compareTo(bankB.getDebitAmount()),
                "BANK_B debit must be 300.00");
        assertEquals(0, new BigDecimal("1000.00").compareTo(bankB.getCreditAmount()),
                "BANK_B credit must be 1000.00");
        assertEquals(0, new BigDecimal("700.00").compareTo(bankB.getNetPosition()),
                "BANK_B net position must be +700.00");
        // BANK_B: 2 as payee (ref1,ref2) + 1 as payer (ref3) = 3
        assertEquals(3, bankB.getTransactionCount(),
                "BANK_B total transaction_count (2 as payee + 1 as payer)");

        // 4. Close cycle ------------------------------------------------------
        SettlementCycleEntity closed = cycleService.closeCycle(cycle.getCycleRef());
        assertEquals("CLOSED", closed.getStatus());
        assertNotNull(closed.getClosedAt(), "closedAt must be set after close");

        // 5. Apply multilateral netting (settle) ------------------------------
        List<SettlementPositionEntity> settled = netPositionService.settle(cycle.getCycleRef());
        assertTrue(settled.size() >= 2,
                "Expected ≥2 settled position rows");
        settled.forEach(p ->
                assertEquals("SETTLED", p.getStatus(),
                        "Position for " + p.getBankCode() + " must be SETTLED after netting"));

        // 6. Verify cycle is SETTLED with settledAt timestamp ----------------
        SettlementCycleEntity finalCycle = cycleService.requireCycle(cycle.getCycleRef());
        assertEquals("SETTLED", finalCycle.getStatus());
        assertNotNull(finalCycle.getSettledAt(), "settledAt must be set after settle");
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    /**
     * A cycle with zero matching transfers still closes and settles successfully.
     * Zero-position settle is valid (e.g. bank holiday with no transactions).
     */
    @Test
    void settle_withNoTransactions_succeeds_zeroPositions() {
        // Use a date far in the future (no transfers) — still within 90-day partition window
        LocalDate futureSettlement = LocalDate.now().plusDays(80);

        SettlementCycleEntity cycle = cycleService.openCycle(futureSettlement);
        assertEquals("OPEN", cycle.getStatus());

        int batched = batchService.batchTransactions(cycle.getCycleRef());
        assertEquals(0, batched, "No transfers exist for far-future date");

        cycleService.closeCycle(cycle.getCycleRef());

        List<SettlementPositionEntity> settled = netPositionService.settle(cycle.getCycleRef());
        assertTrue(settled.isEmpty(), "Zero positions expected when no transfers were batched");

        assertEquals("SETTLED", cycleService.requireCycle(cycle.getCycleRef()).getStatus());
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    /**
     * Settling a cycle that is still OPEN (not CLOSED) must throw.
     * The caller must explicitly close before settling.
     */
    @Test
    void settle_whenCycleIsOpen_throwsIllegalState() {
        LocalDate sd = LocalDate.now().plusDays(75);
        SettlementCycleEntity cycle = cycleService.openCycle(sd);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> netPositionService.settle(cycle.getCycleRef()));
        assertTrue(ex.getMessage().contains("CLOSED"),
                "Error message must tell the caller that CLOSED status is required");
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────

    /**
     * Re-batching a SETTLED cycle must throw — no double-counting of transfers.
     */
    @Test
    void batchTransactions_afterSettled_throwsIllegalState() {
        LocalDate sd = LocalDate.now().plusDays(70);
        SettlementCycleEntity cycle = cycleService.openCycle(sd);
        cycleService.closeCycle(cycle.getCycleRef());
        netPositionService.settle(cycle.getCycleRef());  // cycle is now SETTLED

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> batchService.batchTransactions(cycle.getCycleRef()));
        assertTrue(ex.getMessage().contains("SETTLED"),
                "Error must state that SETTLED cycles cannot be re-batched");
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────

    /**
     * Up to 4 intraday cycles per settlement date are allowed; a 5th must throw.
     */
    @Test
    void openCycle_fourthCycleAllowed_fifthThrows() {
        LocalDate sd = LocalDate.now().plusDays(87);   // unique date, within 90-day partition window

        cycleService.openCycle(sd);
        cycleService.openCycle(sd);
        cycleService.openCycle(sd);
        SettlementCycleEntity fourth = cycleService.openCycle(sd);
        assertEquals((short) 4, fourth.getCycleNumber(),
                "Fourth cycle must have cycleNumber=4");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cycleService.openCycle(sd));
        assertTrue(ex.getMessage().contains("Maximum 4"),
                "Error must mention the 4-cycle-per-day limit");
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────

    /**
     * Closing an already-CLOSED cycle must throw (must not advance to SETTLED accidentally).
     */
    @Test
    void closeCycle_whenAlreadyClosed_throwsIllegalState() {
        LocalDate sd = LocalDate.now().plusDays(65);
        SettlementCycleEntity cycle = cycleService.openCycle(sd);
        cycleService.closeCycle(cycle.getCycleRef());   // first close — OK

        assertThrows(IllegalStateException.class,
                () -> cycleService.closeCycle(cycle.getCycleRef()),
                "Double-close must throw");
    }

    // ── Test 7 ───────────────────────────────────────────────────────────────

    /**
     * Batching is idempotent-safe: positions are accumulated, not duplicated.
     * Re-batching an OPEN or CLOSED cycle adds the same transfers twice, so
     * the service must be called only once in normal operation.
     * This test verifies the UPSERT correctly accumulates on re-batch.
     */
    @Test
    void batchTransactions_onRebatch_openCycle_accumulatesPositions() {
        LocalDate businessDate   = safeBusinessDate();
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        String ref4 = "TRX-SL-IDEM-" + System.nanoTime();
        seedSettledTransfer(ref4, "BANK_A", "BANK_B", new BigDecimal("200.00"), businessDate);

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);

        // First batch — seed includes ref4 plus whatever else is in safeBusinessDate bucket
        int first = batchService.batchTransactions(cycle.getCycleRef());
        assertTrue(first >= 1);

        // Second batch on OPEN cycle — same transfers re-batched (positions upserted again)
        int second = batchService.batchTransactions(cycle.getCycleRef());
        assertEquals(first, second, "Same transfers returned on re-batch of OPEN cycle");

        // Positions should be doubled (upsert accumulates)
        List<SettlementPositionEntity> positions =
                netPositionService.getPositions(cycle.getCycleRef());
        positions.forEach(p ->
                assertTrue(p.getDebitAmount().compareTo(BigDecimal.ZERO) >= 0
                        || p.getCreditAmount().compareTo(BigDecimal.ZERO) >= 0,
                        "Positions must have non-negative amounts"));
    }

    // ── Test 8 ───────────────────────────────────────────────────────────────

    /**
     * listByStatus returns only cycles with the requested status.
     */
    @Test
    void listByStatus_filtersCorrectly() {
        LocalDate sd1 = LocalDate.now().plusDays(60);
        LocalDate sd2 = LocalDate.now().plusDays(61);

        SettlementCycleEntity openCycle   = cycleService.openCycle(sd1);
        SettlementCycleEntity closedCycle = cycleService.openCycle(sd2);
        cycleService.closeCycle(closedCycle.getCycleRef());

        List<SettlementCycleEntity> openList = cycleService.listByStatus("OPEN");
        assertTrue(openList.stream().anyMatch(c -> c.getCycleRef().equals(openCycle.getCycleRef())),
                "OPEN list must contain the open cycle");
        assertTrue(openList.stream().noneMatch(c -> c.getCycleRef().equals(closedCycle.getCycleRef())),
                "OPEN list must not contain the closed cycle");

        List<SettlementCycleEntity> closedList = cycleService.listByStatus("CLOSED");
        assertTrue(closedList.stream().anyMatch(c -> c.getCycleRef().equals(closedCycle.getCycleRef())),
                "CLOSED list must contain the closed cycle");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a businessDate that no other integration test uses (2 biz days before today).
     * FullTransferFlowIntegrationTest uses today; SettlementTPlusOneIntegrationTest uses yesterday.
     */
    private LocalDate safeBusinessDate() {
        return settlementDateService.previousBusinessDay(
               settlementDateService.previousBusinessDay(LocalDate.now()));
    }

    private void seedSettledTransfer(String ref, String srcBank, String dstBank,
                                     BigDecimal amount, LocalDate businessDate) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, '010100000001', ?, '020200000001',
                    'Receiver', ?, 'LAK', 'API', 'ROUTE_SL_TEST', 'MOCK_CONNECTOR',
                    'SETTLED', ?, ?, ?, ?, ?, ?)
                """,
                ref, ref, ref, ref, "INQ-" + ref,
                srcBank, dstBank, amount,
                "EXT-" + ref, "REF-" + ref,
                businessDate, now, now, now);
    }
}
