package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.repository.SettlementInstructionRepository;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.LegacySettlementFormat;
import com.example.switching.settlement.service.SettlementOpsReportService;

class LegacySettlementCompatibilityServiceTest {

    private final SettlementOpsReportService opsReportService = mock(SettlementOpsReportService.class);
    private final SettlementInstructionRepository instructionRepository = mock(SettlementInstructionRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final LegacySettlementCompatibilityService service = new LegacySettlementCompatibilityService(
            opsReportService, instructionRepository, auditLogService);

    @Test
    void settlementFile_exportsLegacyPipeFormatWithChecksumAndAudit() {
        when(opsReportService.load("SC-20260812-C1")).thenReturn(sampleReport());

        var file = service.exportSettlementFile(
                "SC-20260812-C1", LegacySettlementFormat.SETTLEMENT_FILE, "ops-user");

        assertEquals("SC-20260812-C1-settlementfile.txt", file.filename());
        assertTrue(file.content().contains("HDR|LMPS_SETTLEMENT_FILE|1|SC-20260812-C1|2026-08-12|SETTLED|LAK|"));
        assertTrue(file.content().contains("POS|BANK_X|LAK|1000.00|300.00|-700.00|3|SETTLED"));
        assertTrue(file.content().contains("INS|SI-001|BANK_X|BANK_Y|LAK|700.00|CONFIRMED|RTGS-001|"));
        assertTrue(file.content().contains("TRL|2|1|1|1300.00|1300.00"));
        assertTrue(file.content().matches("(?s).*CHK\\|[0-9a-f]{64}\\n$"));
        verify(auditLogService).log(eq("LEGACY_SETTLEMENT_FILE_EXPORTED"), eq("LEGACY_SETTLEMENT_COMPATIBILITY"),
                eq("SC-20260812-C1"), eq("ops-user"), any());
    }

    @Test
    void settlementReportV2_exportsInstructionsSection() {
        when(opsReportService.load("SC-20260812-C1")).thenReturn(sampleReport());

        var file = service.exportSettlementFile(
                "SC-20260812-C1", LegacySettlementFormat.FINLINK_SETTLEMENT_REPORT_V2, "ops-user");

        assertTrue(file.content().startsWith("reportType,cycleRef,settlementDate,status,currency"));
        assertTrue(file.content().contains("\"FINLINK_SETTLEMENT_V2\",\"SC-20260812-C1\""));
        assertTrue(file.content().contains("instructions\n"));
        assertTrue(file.content().contains("\"SI-001\",\"BANK_X\",\"BANK_Y\",\"LAK\",\"700.00\",\"CONFIRMED\",\"RTGS-001\""));
    }

    @Test
    void rtgsJournal_exportsByInstructionRefAndAudits() {
        SettlementInstructionEntity instruction = new SettlementInstructionEntity();
        instruction.setInstructionRef("SI-001");
        instruction.setRtgsMsgId("RTGS-001");
        instruction.setDebtorPspId("BANK_X");
        instruction.setCreditorPspId("BANK_Y");
        instruction.setCurrency("LAK");
        instruction.setNetAmount(new BigDecimal("700.00"));
        instruction.setStatus("CONFIRMED");
        instruction.setRtgsRequestPayload("<pacs.009/>");
        instruction.setRtgsResponsePayload("<accepted/>");
        when(instructionRepository.findByInstructionRef("SI-001")).thenReturn(Optional.of(instruction));

        var journal = service.exportRtgsJournal("SI-001", null, "ops-user");

        assertEquals("SI-001", journal.instructionRef());
        assertEquals("RTGS-001", journal.rtgsMsgId());
        assertEquals("CONFIRMED", journal.status());
        assertNotNull(journal.requestPayload());
        verify(auditLogService).log(eq("LEGACY_RTGS_JOURNAL_EXPORTED"), eq("LEGACY_SETTLEMENT_COMPATIBILITY"),
                eq("SI-001"), eq("ops-user"), any());
    }

    private SettlementOpsReportService.OpsSettlementReport sampleReport() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 12, 10, 30);
        return new SettlementOpsReportService.OpsSettlementReport(
                generatedAt,
                new SettlementOpsReportService.Cycle(1L, "SC-20260812-C1", LocalDate.of(2026, 8, 12),
                        1, "SETTLED", generatedAt.minusHours(2), generatedAt.minusHours(1), generatedAt),
                new SettlementOpsReportService.Summary(2, 1, 2, 1, 1,
                        new BigDecimal("1300.00"), new BigDecimal("1300.00"), "LAK"),
                List.of(
                        new SettlementOpsReportService.Position("BANK_X", "LAK", new BigDecimal("1000.00"),
                                new BigDecimal("300.00"), new BigDecimal("-700.00"), 3, "SETTLED", generatedAt),
                        new SettlementOpsReportService.Position("BANK_Y", "LAK", new BigDecimal("300.00"),
                                new BigDecimal("1000.00"), new BigDecimal("700.00"), 3, "SETTLED", generatedAt)),
                List.of(new SettlementOpsReportService.Instruction("SI-001", "BANK_X", "BANK_Y", "LAK",
                        new BigDecimal("700.00"), "CONFIRMED", "RTGS-001",
                        generatedAt.minusMinutes(10), generatedAt.minusMinutes(5))),
                List.of(new SettlementOpsReportService.Transfer("TRX-001", "CLIENT-001", "BANK_X", "BANK_Y",
                        new BigDecimal("1000.00"), "LAK", "SETTLED", "CONFIRMED", "HIGH",
                        "EXT-001", generatedAt.minusHours(3), generatedAt)),
                List.of());
    }
}
