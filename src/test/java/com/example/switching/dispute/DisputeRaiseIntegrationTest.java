package com.example.switching.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.dispute.dto.DisputeRaiseRequest;
import com.example.switching.dispute.dto.DisputeRaiseResponse;
import com.example.switching.dispute.exception.DisputeAlreadyExistsException;
import com.example.switching.dispute.exception.DisputeTypeInvalidException;
import com.example.switching.dispute.exception.DisputeWindowExpiredException;
import com.example.switching.dispute.service.DisputeRaiseService;

/**
 * Integration tests for dispute raise flow (TC-DISPUTE-RAISE-001 – 004).
 */
class DisputeRaiseIntegrationTest extends AbstractIntegrationTest {

    @Autowired DisputeRaiseService raiseService;
    @Autowired JdbcTemplate        jdbcTemplate;

    // ── TC-DISPUTE-RAISE-001 ─────────────────────────────────────────────────

    @Test
    void raise_withinWindow_succeeds_statusOpen() {
        String txnRef = seedSettledTransaction("BANK_A", "BANK_B", new BigDecimal("50000.00"), LocalDateTime.now());

        DisputeRaiseRequest req = new DisputeRaiseRequest(txnRef, "NOT_RECEIVED", "BANK_A", null, "Did not receive");
        DisputeRaiseResponse resp = raiseService.raise(req);

        assertNotNull(resp.disputeId());
        assertEquals("OPEN",         resp.status());
        assertEquals("NOT_RECEIVED", resp.disputeType());
        assertEquals(txnRef,         resp.txnRef());
        assertNotNull(resp.slaDeadline());
        assertTrue(resp.slaDeadline().isAfter(LocalDateTime.now()), "SLA deadline must be in the future");
    }

    // ── TC-DISPUTE-RAISE-002 ─────────────────────────────────────────────────

    @Test
    void raise_outsideWindow_throwsDisputeWindowExpired() {
        // Created 91 days ago — past the 90-day window
        LocalDateTime oldTime = LocalDateTime.now().minusDays(91);
        String txnRef = seedSettledTransaction("BANK_A", "BANK_B", new BigDecimal("30000.00"), oldTime);

        assertThrows(DisputeWindowExpiredException.class,
                () -> raiseService.raise(new DisputeRaiseRequest(txnRef, "WRONG_AMOUNT", "BANK_A", null, null)),
                "Day 91 must throw DisputeWindowExpiredException (LFP-9001)");
    }

    // ── TC-DISPUTE-RAISE-003 ─────────────────────────────────────────────────

    @Test
    void raise_slaDeadlineCorrectPerType() {
        // TECHNICAL_ERROR → 1 day; FRAUD → 5 days
        String txn1 = seedSettledTransaction("BANK_A", "BANK_B", new BigDecimal("10000.00"), LocalDateTime.now());
        String txn2 = seedSettledTransaction("BANK_A", "BANK_B", new BigDecimal("10000.00"), LocalDateTime.now());

        DisputeRaiseResponse tech  = raiseService.raise(new DisputeRaiseRequest(txn1, "TECHNICAL_ERROR", "BANK_A", null, null));
        DisputeRaiseResponse fraud = raiseService.raise(new DisputeRaiseRequest(txn2, "FRAUD",           "BANK_A", null, null));

        long techDays  = java.time.Duration.between(LocalDateTime.now(), tech.slaDeadline()).toDays();
        long fraudDays = java.time.Duration.between(LocalDateTime.now(), fraud.slaDeadline()).toDays();

        assertTrue(techDays  >= 0 && techDays  <= 1, "TECHNICAL_ERROR SLA = 1 day, got " + techDays);
        assertTrue(fraudDays >= 4 && fraudDays <= 5, "FRAUD SLA = 5 days, got " + fraudDays);
    }

    // ── TC-DISPUTE-RAISE-004 ─────────────────────────────────────────────────

    @Test
    void raise_duplicateActiveDispute_throwsDisputeAlreadyExists() {
        String txnRef = seedSettledTransaction("BANK_A", "BANK_B", new BigDecimal("20000.00"), LocalDateTime.now());

        // First raise succeeds
        raiseService.raise(new DisputeRaiseRequest(txnRef, "WRONG_AMOUNT", "BANK_A", null, null));

        // Second raise on same txnRef must throw LFP-9003
        assertThrows(DisputeAlreadyExistsException.class,
                () -> raiseService.raise(new DisputeRaiseRequest(txnRef, "DUPLICATE_CHARGE", "BANK_A", null, null)),
                "Active dispute for same txnRef must throw DisputeAlreadyExistsException (LFP-9003)");
    }

    // ── TC-DISPUTE-RAISE-005 (invalid type) ──────────────────────────────────

    @Test
    void raise_invalidType_throwsDisputeTypeInvalid() {
        assertThrows(DisputeTypeInvalidException.class,
                () -> raiseService.raise(new DisputeRaiseRequest("TXN-X", "UNKNOWN_TYPE", "BANK_A", null, null)),
                "Invalid dispute type must throw DisputeTypeInvalidException (LFP-9002)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String seedSettledTransaction(String sourcePsp, String destPsp, BigDecimal amount, LocalDateTime createdAt) {
        String txnRef = "TXN-DISP-" + System.nanoTime();
        LocalDate today = LocalDate.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, 'ACC_SRC', ?, 'ACC_DST', 'Test Merchant',
                    ?, 'LAK', 'DISPUTE_TEST', 'ROUTE_A', 'CONN_A',
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                txnRef, txnRef, "FLOW-DISP",
                sourcePsp, destPsp,
                amount,
                txnRef, txnRef,
                today, createdAt, createdAt, createdAt);
        return txnRef;
    }
}
