package com.example.switching.crossborder.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.crossborder.service.CrossBorderLegacyRailFacadeService;

class CrossBorderLegacyRailFacadeServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final CrossBorderLegacyRailFacadeService service = new CrossBorderLegacyRailFacadeService(jdbc, audit);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void settlementFile_mapsItmxToPromptPaySourceAndAddsChecksum() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 13);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("PROMPTPAY"), eq(date)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(sql.contains("cross_border_rail_message")
                            ? messageResultSet("PROMPTPAY")
                            : reconciliationResultSet("PROMPTPAY"), 0));
                });

        var file = service.exportFile("ITMX", "SETTLEMENT_FILE", date, "ops-user");

        assertEquals("crossborder-itmx-settlement-file-2026-08-13.txt", file.filename());
        assertTrue(file.content().contains("HDR|ITMX|PROMPTPAY|SETTLEMENT_FILE|2026-08-13|1|1|1000.00|1000.00"));
        assertTrue(file.content().contains("MSG|OUTBOUND|EXT-1|INT-1|pacs.008|ACKNOWLEDGED|ACK"));
        assertTrue(file.content().contains("REC|EXT-1|INT-1|1000.00|1000.00|LAK|MATCHED"));
        assertTrue(file.content().matches("(?s).*CHK\\|[0-9a-f]{64}\\n$"));
        verify(audit).log(eq("CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED"), eq("CROSS_BORDER_LEGACY_RAIL"),
                eq("ITMX:SETTLEMENT_FILE:2026-08-13"), eq("ops-user"), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reportCsv_keepsNapasSourceRail() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 13);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("NAPAS"), eq(date)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(sql.contains("cross_border_rail_message")
                            ? messageResultSet("NAPAS")
                            : reconciliationResultSet("NAPAS"), 0));
                });

        var file = service.exportFile("NAPAS", "SETTLEMENT_REPORT", date, "ops-user");

        assertEquals("crossborder-napas-settlement-report-2026-08-13.csv", file.filename());
        assertTrue(file.content().startsWith("section,legacyRail,sourceRail,variant,statementDate,count"));
        assertTrue(file.content().contains("\"SUMMARY\",\"NAPAS\",\"NAPAS\",\"SETTLEMENT_REPORT\",\"2026-08-13\",\"2\""));
    }

    private ResultSet messageResultSet(String rail) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("rail")).thenReturn(rail);
        when(rs.getString("direction")).thenReturn("OUTBOUND");
        when(rs.getString("external_ref")).thenReturn("EXT-1");
        when(rs.getString("internal_ref")).thenReturn("INT-1");
        when(rs.getString("message_type")).thenReturn("pacs.008");
        when(rs.getString("status")).thenReturn("ACKNOWLEDGED");
        when(rs.getString("acknowledgement_code")).thenReturn("ACK");
        when(rs.getObject("settlement_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 8, 13));
        when(rs.getObject("received_at", LocalDateTime.class)).thenReturn(LocalDateTime.of(2026, 8, 13, 10, 0));
        when(rs.getObject("sent_at", LocalDateTime.class)).thenReturn(LocalDateTime.of(2026, 8, 13, 10, 1));
        when(rs.getObject("completed_at", LocalDateTime.class)).thenReturn(LocalDateTime.of(2026, 8, 13, 10, 2));
        return rs;
    }

    private ResultSet reconciliationResultSet(String rail) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("rail")).thenReturn(rail);
        when(rs.getObject("statement_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 8, 13));
        when(rs.getString("external_ref")).thenReturn("EXT-1");
        when(rs.getString("internal_ref")).thenReturn("INT-1");
        when(rs.getBigDecimal("external_amount")).thenReturn(new BigDecimal("1000.00"));
        when(rs.getBigDecimal("internal_amount")).thenReturn(new BigDecimal("1000.00"));
        when(rs.getString("currency")).thenReturn("LAK");
        when(rs.getString("status")).thenReturn("MATCHED");
        when(rs.getString("discrepancy_reason")).thenReturn(null);
        when(rs.getString("evidence_sha256")).thenReturn("a".repeat(64));
        return rs;
    }
}
