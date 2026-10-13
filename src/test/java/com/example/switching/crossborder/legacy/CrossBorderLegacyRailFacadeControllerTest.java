package com.example.switching.crossborder.legacy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.switching.crossborder.controller.CrossBorderLegacyRailFacadeController;
import com.example.switching.crossborder.service.CrossBorderLegacyRailFacadeService;

class CrossBorderLegacyRailFacadeControllerTest {

    private final CrossBorderLegacyRailFacadeService service = mock(CrossBorderLegacyRailFacadeService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CrossBorderLegacyRailFacadeController(service))
            .build();

    @Test
    void itmxSettlementFile_returnsLegacyFile() throws Exception {
        when(service.exportFile(eq("ITMX"), eq("SETTLEMENT_FILE"), eq(LocalDate.of(2026, 8, 13)), any()))
                .thenReturn(new CrossBorderLegacyRailFacadeService.LegacyRailFile(
                        "crossborder-itmx-settlement-file-2026-08-13.txt", "HDR|ITMX|PROMPTPAY\n"));

        mockMvc.perform(get("/crossborder/itmx/settlementfile").param("statementDate", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"crossborder-itmx-settlement-file-2026-08-13.txt\""))
                .andExpect(content().string("HDR|ITMX|PROMPTPAY\n"));
    }

    @Test
    void napasBankReport_returnsCsv() throws Exception {
        when(service.exportFile(eq("NAPAS"), eq("SETTLEMENT_BANK_REPORT"), eq(LocalDate.of(2026, 8, 13)), any()))
                .thenReturn(new CrossBorderLegacyRailFacadeService.LegacyRailFile(
                        "crossborder-napas-settlement-bank-report-2026-08-13.csv", "section,legacyRail\n"));

        mockMvc.perform(get("/crossborder/napas/settlementbankreport").param("statementDate", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(content().string("section,legacyRail\n"));
    }

    @Test
    void lfxSettlementFile_returnsGuardedEmptyRailFile() throws Exception {
        when(service.exportFile(eq("LFX"), eq("SETTLEMENT_FILE"), eq(LocalDate.of(2026, 8, 13)), any()))
                .thenReturn(new CrossBorderLegacyRailFacadeService.LegacyRailFile(
                        "crossborder-lfx-settlement-file-2026-08-13.txt", "HDR|LFX|LFX\n"));

        mockMvc.perform(get("/lfx/settlementfile").param("statementDate", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(content().string("HDR|LFX|LFX\n"));
    }
}
