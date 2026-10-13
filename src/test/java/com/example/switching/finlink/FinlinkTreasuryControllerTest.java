package com.example.switching.finlink;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FinlinkTreasuryControllerTest {

    private final FinlinkTreasuryService service = mock(FinlinkTreasuryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FinlinkTreasuryController(service)).build();

    @Test
    void transitFundMove_postsLegacyTreasuryMove() throws Exception {
        when(service.recordTransitFundMove(any(), any())).thenReturn(response("FL-TFM-1", "TRANSIT_FUND_MOVE", "POSTED"));

        mockMvc.perform(post("/finlink/transit_fund_move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"idem-1","businessDate":"2026-08-12","currency":"LAK","amount":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventRef").value("FL-TFM-1"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.journalId").exists());
    }

    @Test
    void informLifmis_recordsInformEvent() throws Exception {
        when(service.recordLifmisInform(any(), any())).thenReturn(response("FL-LIFMIS-1", "LIFMIS_INFORM", "INFORMED"));

        mockMvc.perform(post("/finlink/inform_LIFMIS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"idem-2","businessDate":"2026-08-12","currency":"LAK","amount":500,"lifmisReference":"LIFMIS-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("LIFMIS_INFORM"))
                .andExpect(jsonPath("$.status").value("INFORMED"));
    }

    @Test
    void easyTaxFile_exportsSettlementFile() throws Exception {
        when(service.exportSettlementFile(eq("EASYTAX_SETTLEMENT"), eq(LocalDate.of(2026, 8, 12)), any()))
                .thenReturn(new FinlinkTreasuryService.FinlinkFile("finlink-easytax.txt", "HDR|EASYTAX_SETTLEMENT\n"));

        mockMvc.perform(get("/finlink/settlementfileeasytax").param("businessDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(content().string("HDR|EASYTAX_SETTLEMENT\n"));
    }

    private FinlinkTreasuryService.FinlinkEventResponse response(String eventRef, String eventType, String status) {
        return new FinlinkTreasuryService.FinlinkEventResponse(
                eventRef, eventType, "TAX", LocalDate.of(2026, 8, 12), "LAK", new BigDecimal("1000"),
                "FINLINK_TRANSIT_CLEARING", "FINLINK_SETTLEMENT_PAYABLE", "CP-1", "EXT-1",
                status, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
}
