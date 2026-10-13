package com.example.switching.settlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.switching.settlement.controller.LegacySettlementCompatibilityController;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.LegacyFile;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.LegacySettlementFormat;
import com.example.switching.settlement.service.LegacySettlementCompatibilityService.RtgsJournal;

class LegacySettlementCompatibilityControllerTest {

    private final LegacySettlementCompatibilityService service = mock(LegacySettlementCompatibilityService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new LegacySettlementCompatibilityController(service))
            .build();

    @Test
    void settlementFileEndpoint_returnsLegacyFile() throws Exception {
        when(service.exportSettlementFile(eq("SC-1"), eq(LegacySettlementFormat.SETTLEMENT_FILE), any()))
                .thenReturn(new LegacyFile("SC-1-settlementfile.txt", "HDR|LMPS_SETTLEMENT_FILE\n"));

        mockMvc.perform(get("/settlementfile").param("cycleRef", "SC-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"SC-1-settlementfile.txt\""))
                .andExpect(content().string("HDR|LMPS_SETTLEMENT_FILE\n"));
    }

    @Test
    void finlinkSettlementV2Endpoint_usesV2Format() throws Exception {
        when(service.exportSettlementFile(eq("SC-1"), eq(LegacySettlementFormat.FINLINK_SETTLEMENT_REPORT_V2), any()))
                .thenReturn(new LegacyFile("SC-1-finlink-report-settlement-v2.csv", "reportType,cycleRef\n"));

        mockMvc.perform(get("/finlink/report/settlement_v2").param("cycleRef", "SC-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("reportType,cycleRef\n"));
    }

    @Test
    void getRtgsJournalEndpoint_returnsJournal() throws Exception {
        when(service.exportRtgsJournal(eq("SI-001"), eq(null), any()))
                .thenReturn(new RtgsJournal("SI-001", "RTGS-001", "BANK_X", "BANK_Y", "LAK",
                        new BigDecimal("700.00"), "CONFIRMED", null, null, null,
                        "<pacs.009/>", "<accepted/>"));

        mockMvc.perform(get("/finlink/getrtgsjournal").param("instructionRef", "SI-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructionRef").value("SI-001"))
                .andExpect(jsonPath("$.rtgsMsgId").value("RTGS-001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
