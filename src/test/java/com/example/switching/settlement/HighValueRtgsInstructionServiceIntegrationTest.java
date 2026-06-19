package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.service.HighValueRtgsInstructionService;

class HighValueRtgsInstructionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HighValueRtgsInstructionService highValueRtgsInstructionService;

    @Test
    void generatePendingInstruction_highValueRtgsTransferCreatesIdempotentMakerCheckerInstruction() {
        String transferRef = "HV-RTGS-" + System.nanoTime();
        seedTransfer(transferRef, new BigDecimal("600000000.00"), "RTGS", true);

        SettlementInstructionEntity first =
                highValueRtgsInstructionService.generatePendingInstruction(transferRef);
        SettlementInstructionEntity second =
                highValueRtgsInstructionService.generatePendingInstruction(transferRef);

        assertEquals(first.getId(), second.getId());
        assertEquals("HV-" + transferRef, first.getInstructionRef());
        assertEquals("HIGH_VALUE_TRANSFER", first.getSourceType());
        assertEquals(transferRef, first.getTransferRef());
        assertNull(first.getCycleId());
        assertEquals("BANK_A", first.getDebtorPspId());
        assertEquals("BANK_B", first.getCreditorPspId());
        assertEquals("LAK", first.getCurrency());
        assertEquals(0, new BigDecimal("600000000.00").compareTo(first.getNetAmount()));
        assertEquals("PENDING_APPROVAL", first.getStatus());
    }

    @Test
    void generatePendingInstruction_rejectsDnsTransfer() {
        String transferRef = "DNS-" + System.nanoTime();
        seedTransfer(transferRef, new BigDecimal("1000.00"), "DNS", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> highValueRtgsInstructionService.generatePendingInstruction(transferRef));

        assertTrue(ex.getMessage().contains("not routed to high-value RTGS"));
    }

    private void seedTransfer(String transferRef, BigDecimal amount, String settlementMethod, boolean highValue) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, 'BANK_A', '010100000001', 'BANK_B', '020200000001',
                    'Receiver', ?, 'LAK', 'API', 'ROUTE_HV_RTGS', 'MOCK_BANK_B_CONNECTOR',
                    'SETTLED', ?, ?, ?, ?, ?, ?, ?, ?)
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
                LocalDate.now(),
                now,
                now,
                now);
    }
}
