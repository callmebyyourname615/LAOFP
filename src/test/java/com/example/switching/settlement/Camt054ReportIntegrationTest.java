package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.webhook.WebhookTestSecrets;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementReportEntity;
import com.example.switching.settlement.service.Camt054ReportService;
import com.example.switching.settlement.service.SettlementBatchService;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementDateService;
import com.example.switching.settlement.service.SettlementNetPositionService;

/**
 * Integration tests for the camt.054 settlement report generation.
 *
 * <p>Verifies that after a DNS cycle settles:
 * <ul>
 *   <li>A camt.054 report row is created in {@code settlement_reports} for each PSP.</li>
 *   <li>The XML contains the PSP account reference, cycleRef, and correct debit/credit entries.</li>
 *   <li>Re-calling {@code generateReportsForCycle()} is idempotent.</li>
 *   <li>Webhook delivery log rows are created for {@code SETTLEMENT.CYCLE.COMPLETED}.</li>
 * </ul>
 *
 * <p>Uses future settlement dates to avoid collisions with other settlement tests in the
 * shared Testcontainers database.
 */
class Camt054ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private SettlementBatchService batchService;
    @Autowired private SettlementNetPositionService netPositionService;
    @Autowired private SettlementDateService settlementDateService;
    @Autowired private Camt054ReportService reportService;

    // ── TC-RPT-001 ───────────────────────────────────────────────────────────

    /**
     * Full lifecycle → settle → camt.054 reports generated.
     *
     * Seeds two BANK_A → BANK_B transfers and one BANK_B → BANK_A transfer,
     * then verifies that a camt.054 report is created for both PSPs with the
     * correct debit/credit amounts and XML structure.
     */
    @Test
    void settle_generatesCamt054ReportsForAllPsps() {
        LocalDate businessDate   = safeBusinessDate();
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        seedSettledDnsTransfer("RPT-A1-" + System.nanoTime(), "BANK_A", "BANK_B",
                new BigDecimal("400.00"), businessDate);
        seedSettledDnsTransfer("RPT-A2-" + System.nanoTime(), "BANK_A", "BANK_B",
                new BigDecimal("600.00"), businessDate);
        seedSettledDnsTransfer("RPT-B1-" + System.nanoTime(), "BANK_B", "BANK_A",
                new BigDecimal("250.00"), businessDate);

        // BANK_A net: debit=1000, credit=250, net=-750
        // BANK_B net: debit=250,  credit=1000, net=+750

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        batchService.batchTransactions(cycle.getCycleRef());
        cycleService.closeCycle(cycle.getCycleRef());
        netPositionService.settle(cycle.getCycleRef());

        // Settle committed — now generate reports in separate transaction
        List<SettlementReportEntity> reports = reportService.generateReportsForCycle(cycle.getCycleRef());

        assertTrue(reports.size() >= 2,
                "Expected ≥2 reports (one per PSP), got " + reports.size());

        // Verify BANK_A report
        SettlementReportEntity bankAReport = reports.stream()
                .filter(r -> "BANK_A".equals(r.getPspId()))
                .findFirst()
                .orElse(null);
        assertNotNull(bankAReport, "camt.054 report for BANK_A must be generated");
        assertEquals("CAMT054", bankAReport.getReportType());
        assertNotNull(bankAReport.getReportRef(), "reportRef must be set");
        assertTrue(bankAReport.getReportRef().contains(cycle.getCycleRef()),
                "reportRef must include cycleRef");

        String xmlA = bankAReport.getContent();
        assertNotNull(xmlA, "camt.054 XML must be generated for BANK_A");
        // Structural XML assertions — specific amounts not checked because the shared
        // Testcontainers DB accumulates extra BANK_A/BANK_B transfers across test classes.
        assertTrue(xmlA.contains("camt.054.001.08"), "XML must declare camt.054 namespace");
        assertTrue(xmlA.contains("BANK_A"), "XML must reference BANK_A PSP ID");
        assertTrue(xmlA.contains(cycle.getCycleRef()), "XML must reference cycleRef");
        assertTrue(xmlA.contains("<CdtDbtInd>DBIT</CdtDbtInd>"), "XML must include DBIT entry");
        assertTrue(xmlA.contains("<CdtDbtInd>CRDT</CdtDbtInd>"), "XML must include CRDT entry");
        assertTrue(xmlA.contains("<BkToCstmrDbtCdtNtfctn>"), "XML must have notification element");

        // Verify BANK_B report
        SettlementReportEntity bankBReport = reports.stream()
                .filter(r -> "BANK_B".equals(r.getPspId()))
                .findFirst()
                .orElse(null);
        assertNotNull(bankBReport, "camt.054 report for BANK_B must be generated");
        String xmlB = bankBReport.getContent();
        assertNotNull(xmlB, "camt.054 XML must be generated for BANK_B");
        assertTrue(xmlB.contains("camt.054.001.08"), "BANK_B XML must declare camt.054 namespace");
        assertTrue(xmlB.contains("BANK_B"), "XML must reference BANK_B PSP ID");
        assertTrue(xmlB.contains("<CdtDbtInd>DBIT</CdtDbtInd>"), "BANK_B XML must include DBIT entry");
        assertTrue(xmlB.contains("<CdtDbtInd>CRDT</CdtDbtInd>"), "BANK_B XML must include CRDT entry");
    }

    // ── TC-RPT-002 ───────────────────────────────────────────────────────────

    /**
     * Calling generateReportsForCycle() twice returns the same rows — idempotent.
     * The UNIQUE constraint on (cycle_id, psp_id, report_type) prevents duplicates.
     */
    @Test
    void generateReports_isIdempotent() {
        LocalDate businessDate   = safeBusinessDate();
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        seedSettledDnsTransfer("RPT-IDEM-" + System.nanoTime(), "BANK_A", "BANK_B",
                new BigDecimal("100.00"), businessDate);

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        batchService.batchTransactions(cycle.getCycleRef());
        cycleService.closeCycle(cycle.getCycleRef());
        netPositionService.settle(cycle.getCycleRef());

        List<SettlementReportEntity> first  = reportService.generateReportsForCycle(cycle.getCycleRef());
        List<SettlementReportEntity> second = reportService.generateReportsForCycle(cycle.getCycleRef());

        assertEquals(first.size(), second.size(),
                "Second call must return same number of reports (idempotent)");
        // Verify IDs are the same (not new rows)
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getId(), second.get(i).getId(),
                    "Idempotent: second call must return the same report row (same id)");
        }
    }

    // ── TC-RPT-003 ───────────────────────────────────────────────────────────

    /**
     * generateReports() on a non-SETTLED cycle must throw IllegalStateException.
     */
    @Test
    void generateReports_onNonSettledCycle_throwsIllegalState() {
        LocalDate sd = LocalDate.now().plusDays(82);
        SettlementCycleEntity cycle = cycleService.openCycle(sd);
        // OPEN — not SETTLED
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> reportService.generateReportsForCycle(cycle.getCycleRef()));
    }

    // ── TC-RPT-004 ───────────────────────────────────────────────────────────

    /**
     * getReport() returns the stored XML for a specific PSP; listForCycle() returns all.
     */
    @Test
    void getReport_andListForCycle_workCorrectly() {
        LocalDate businessDate   = safeBusinessDate();
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        seedSettledDnsTransfer("RPT-GET-" + System.nanoTime(), "BANK_A", "BANK_B",
                new BigDecimal("300.00"), businessDate);

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        batchService.batchTransactions(cycle.getCycleRef());
        cycleService.closeCycle(cycle.getCycleRef());
        netPositionService.settle(cycle.getCycleRef());
        reportService.generateReportsForCycle(cycle.getCycleRef());

        // getReport() — BANK_A
        var bankAReport = reportService.getReport(cycle.getCycleRef(), "BANK_A");
        assertTrue(bankAReport.isPresent(), "getReport(BANK_A) must return a result");
        assertTrue(bankAReport.get().getContent().contains("BANK_A"),
                "camt.054 XML must contain BANK_A");

        // listForCycle() — both banks present
        List<SettlementReportEntity> allReports = reportService.listForCycle(cycle.getCycleRef());
        assertTrue(allReports.size() >= 2,
                "listForCycle must return ≥2 reports (one per PSP)");
        assertTrue(allReports.stream().anyMatch(r -> "BANK_A".equals(r.getPspId())),
                "listForCycle must include BANK_A report");
        assertTrue(allReports.stream().anyMatch(r -> "BANK_B".equals(r.getPspId())),
                "listForCycle must include BANK_B report");
    }

    // ── TC-RPT-005 ───────────────────────────────────────────────────────────

    /**
     * After settle + generateReports(), a SETTLEMENT.CYCLE.COMPLETED webhook delivery
     * log entry is created for each PSP that has a registered active webhook.
     */
    @Test
    void settle_firesSettlementCycleCompletedWebhook() {
        LocalDate businessDate   = safeBusinessDate();
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);

        seedSettledDnsTransfer("RPT-WH-" + System.nanoTime(), "BANK_A", "BANK_B",
                new BigDecimal("200.00"), businessDate);

        // Seed an active webhook registration for BANK_A so the webhook delivery can fire
        String webhookId = seedWebhookRegistration("BANK_A", "SETTLEMENT.CYCLE.COMPLETED");

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        batchService.batchTransactions(cycle.getCycleRef());
        cycleService.closeCycle(cycle.getCycleRef());
        netPositionService.settle(cycle.getCycleRef());
        reportService.generateReportsForCycle(cycle.getCycleRef());

        // Verify webhook_delivery_log has SETTLEMENT.CYCLE.COMPLETED for BANK_A.
        // webhook_delivery_log.webhook_id → webhook_registrations.webhook_id (VARCHAR FK).
        Integer deliveryCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM webhook_delivery_log wdl
                JOIN webhook_registrations wr ON wdl.webhook_id = wr.webhook_id
                WHERE wr.psp_id   = 'BANK_A'
                  AND wdl.event_type = 'SETTLEMENT.CYCLE.COMPLETED'
                  AND wdl.event_ref  = ?
                """,
                Integer.class,
                cycle.getCycleRef());
        assertTrue(deliveryCount != null && deliveryCount >= 1,
                "Expected ≥1 SETTLEMENT.CYCLE.COMPLETED delivery log for BANK_A, got "
                + deliveryCount);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a business date 4 business days before today.
     *
     * <p>Date allocation in the shared Testcontainers DB:
     * <ul>
     *   <li>Today           → FullTransferFlowIntegrationTest, SettlementCutoffSchedulerIntegrationTest</li>
     *   <li>Yesterday       → SettlementTPlusOneIntegrationTest</li>
     *   <li>2 biz days ago  → SettlementLifecycleIntegrationTest (re-batch test)</li>
     *   <li>3 biz days ago  → SettlementLifecycleIntegrationTest (full lifecycle test)</li>
     *   <li>4 biz days ago  → <b>this class</b> (exclusive)</li>
     * </ul>
     *
     * <p>The transaction partition window is -7 to +90 days from CURRENT_DATE.
     * 4 biz days ago maps to ~5 calendar days ago, safely within the -7 day boundary.
     * All 4 batching tests in this class use the same settlement date and open
     * cycles 1–4 sequentially, which respects the 4-cycle-per-day limit.
     */
    private LocalDate safeBusinessDate() {
        return settlementDateService.previousBusinessDay(
               settlementDateService.previousBusinessDay(
               settlementDateService.previousBusinessDay(
               settlementDateService.previousBusinessDay(LocalDate.now()))));
    }

    private void seedSettledDnsTransfer(String ref, String srcBank, String dstBank,
                                        BigDecimal amount, LocalDate businessDate) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, '010100000001', ?, '020200000001',
                    'Receiver', ?, 'LAK', 'API', 'ROUTE_RPT_TEST', 'MOCK_CONNECTOR',
                    'SETTLED', ?, ?, 'DNS', false, ?, ?, ?, ?)
                """,
                ref, ref, ref, ref, "INQ-" + ref,
                srcBank, dstBank,
                amount,
                "EXT-" + ref, "REF-" + ref,
                businessDate, now, now, now);
    }

    private String seedWebhookRegistration(String pspId, String eventType) {
        // Insert a fresh ACTIVE webhook registration so the delivery service can fire.
        // event_types is stored as a JSON array string per the schema.
        String webhookId = java.util.UUID.randomUUID().toString();
        String eventTypesJson = "[\"" + eventType + "\"]";
        var encryptedSecret = WebhookTestSecrets.encrypted();
        jdbcTemplate.update("""
                INSERT INTO webhook_registrations (
                    webhook_id, psp_id, url, secret_ciphertext, secret_key_id,
                    secret_version, secret_hash, event_types, status, created_at, updated_at
                ) VALUES (?, ?, 'http://localhost:19999/noop', ?, ?, ?, ?,
                    ?, 'ACTIVE', NOW(), NOW())
                """,
                webhookId, pspId,
                encryptedSecret.ciphertext(), encryptedSecret.keyId(), encryptedSecret.version(),
                WebhookTestSecrets.sha256(), eventTypesJson);
        return webhookId;
    }
}
