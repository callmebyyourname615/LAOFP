package com.example.switching.finlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.ledger.FinancialControlLedgerService;

class FinlinkTreasuryServiceFormattingTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FinancialControlLedgerService ledger = mock(FinancialControlLedgerService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final FinlinkTreasuryService service = new FinlinkTreasuryService(jdbc, ledger, audit);

    @Test
    @SuppressWarnings("unchecked")
    void exportSettlementFile_buildsHeaderDetailsTrailerChecksumAndAudit() {
        LocalDate businessDate = LocalDate.of(2026, 8, 12);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("EASYTAX_SETTLEMENT"), eq(businessDate)))
                .thenReturn(List.of(event("FL-1", "EASYTAX_SETTLEMENT", new BigDecimal("1000.00")),
                        event("FL-2", "EASYTAX_SETTLEMENT", new BigDecimal("250.00"))));

        var file = service.exportSettlementFile("EASYTAX_SETTLEMENT", businessDate, "ops-user");

        assertEquals("finlink-easytax-settlement-2026-08-12.txt", file.filename());
        assertTrue(file.content().contains("HDR|EASYTAX_SETTLEMENT|2026-08-12|2|1250.00"));
        assertTrue(file.content().contains("DTL|FL-1|EASYTAX|LAK|1000.00|FINLINK_TRANSIT_CLEARING|FINLINK_TAX_PAYABLE"));
        assertTrue(file.content().contains("TRL|2|1250.00"));
        assertTrue(file.content().matches("(?s).*CHK\\|[0-9a-f]{64}\\n$"));
        verify(audit).log(eq("FINLINK_SETTLEMENT_FILE_EXPORTED"), eq("FINLINK_TREASURY_EVENT"),
                eq("EASYTAX_SETTLEMENT:2026-08-12"), eq("ops-user"), any());
    }

    private FinlinkTreasuryService.FinlinkEventResponse event(String ref, String type, BigDecimal amount) {
        return new FinlinkTreasuryService.FinlinkEventResponse(
                ref, type, "EASYTAX", LocalDate.of(2026, 8, 12), "LAK", amount,
                "FINLINK_TRANSIT_CLEARING", "FINLINK_TAX_PAYABLE", "CP-1", "EXT-1", "POSTED",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
