package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.service.SettlementBatchService;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementDateService;

class SettlementTPlusOneIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private SettlementBatchService batchService;
    @Autowired private SettlementDateService settlementDateService;

    @Test
    void batchTransactions_forSettlementDate_batchesPreviousBusinessDateOnly() {
        // Use the previous business day so this test's transfers don't overlap with
        // today's SETTLED transfers created by other tests in the shared DB.
        LocalDate businessDate = settlementDateService.previousBusinessDay(LocalDate.now());
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);
        String tRef = "TRX-TPLUS1-T-" + System.nanoTime();
        String sameDayRef = "TRX-TPLUS1-S-" + System.nanoTime();

        seedSettledTransfer(tRef, businessDate);
        seedSettledTransfer(sameDayRef, settlementDate);

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);

        int batched = batchService.batchTransactions(cycle.getCycleRef());

        assertEquals(1, batched);
        List<String> itemRefs = jdbcTemplate.queryForList(
                "SELECT DISTINCT transaction_ref FROM settlement_items WHERE cycle_id = ?",
                String.class,
                cycle.getId());
        assertTrue(itemRefs.contains(tRef), "T business-date transaction must be included in T+1 cycle");
        assertFalse(itemRefs.contains(sameDayRef), "T+1 same-day transaction must wait for the next cycle");
    }

    @Test
    void batchTransactions_excludesHighValueRtgsTransfersFromDnsNetting() {
        LocalDate businessDate = settlementDateService.previousBusinessDay(LocalDate.now().plusDays(10));
        LocalDate settlementDate = settlementDateService.nextBusinessDay(businessDate);
        String dnsRef = "TRX-DNS-" + System.nanoTime();
        String rtgsRef = "TRX-RTGS-" + System.nanoTime();

        seedSettledTransfer(dnsRef, businessDate, new BigDecimal("1000.00"), "DNS", false);
        seedSettledTransfer(rtgsRef, businessDate, new BigDecimal("600000000.00"), "RTGS", true);

        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);

        int batched = batchService.batchTransactions(cycle.getCycleRef());

        assertEquals(1, batched);
        List<String> itemRefs = jdbcTemplate.queryForList(
                "SELECT DISTINCT transaction_ref FROM settlement_items WHERE cycle_id = ?",
                String.class,
                cycle.getId());
        assertTrue(itemRefs.contains(dnsRef), "DNS transfer must be included in DNS netting");
        assertFalse(itemRefs.contains(rtgsRef), "High-value RTGS transfer must bypass DNS netting");
    }

    @Test
    void openCycle_withoutDate_defaultsToNextBusinessDay() {
        LocalDate today = LocalDate.now();
        LocalDate expectedSettlementDate = settlementDateService.nextBusinessDay(today);

        SettlementCycleEntity cycle = cycleService.openCycle(null);

        assertEquals(expectedSettlementDate, cycle.getSettlementDate());
    }

    private void seedSettledTransfer(String transferRef, LocalDate businessDate) {
        seedSettledTransfer(transferRef, businessDate, new BigDecimal("1000.00"), "DNS", false);
    }

    private void seedSettledTransfer(String transferRef, LocalDate businessDate,
                                     BigDecimal amount, String settlementMethod, boolean highValue) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, 'BANK_A', '010100000001', 'BANK_B', '020200000001',
                    'Receiver', ?, 'LAK', 'API', 'ROUTE_TPLUS1', 'MOCK_BANK_B_CONNECTOR',
                    'READY_FOR_SETTLEMENT', ?, ?, ?, ?, ?, ?, NULL, ?)
                """,
                transferRef,
                transferRef,
                transferRef,
                transferRef,
                "INQ-" + transferRef,
                amount,
                "EXT-" + transferRef,
                "REF-" + transferRef,
                settlementMethod,
                highValue,
                businessDate,
                now,
                now);
    }
}
