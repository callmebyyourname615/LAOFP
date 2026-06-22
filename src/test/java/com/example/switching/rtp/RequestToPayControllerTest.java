package com.example.switching.rtp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.switching.rtp.controller.RequestToPayController;
import com.example.switching.rtp.dto.RtpCreateResult;
import com.example.switching.rtp.dto.RtpRequestResponse;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.service.RtpRequestService;
import com.example.switching.rtp.service.RtpAuthorisationService;

@ExtendWith(MockitoExtension.class)
class RequestToPayControllerTest {

    @Mock
    private RtpRequestService requestService;

    @Mock
    private RtpAuthorisationService authorisationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RequestToPayController(requestService, authorisationService))
                .build();
    }

    @Test
    void createReturns201AndLocationForNewRequest() throws Exception {
        UUID id = UUID.randomUUID();
        RtpRequestResponse response = response(id);
        when(requestService.create(any(), any())).thenReturn(new RtpCreateResult(response, true));

        mockMvc.perform(post("/v1/rtp/requests")
                        .principal(bankPrincipal("BANK_A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestCorrelationId":"RTP-CONTROLLER-1",
                                  "payeeParticipantId":"BANK_A",
                                  "payerParticipantId":"BANK_B",
                                  "payeeAccount":"010100000001",
                                  "requestedAmount":1000.00,
                                  "currency":"LAK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/rtp/requests/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_AUTH"));
    }

    @Test
    void malformedCreateRequestIsRejectedBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/v1/rtp/requests")
                        .principal(bankPrincipal("BANK_A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestCorrelationId":"",
                                  "payeeParticipantId":"BANK_A",
                                  "payerParticipantId":"BANK_B",
                                  "payeeAccount":"010100000001",
                                  "requestedAmount":-1,
                                  "currency":"INVALID"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static UsernamePasswordAuthenticationToken bankPrincipal(String participantId) {
        return new UsernamePasswordAuthenticationToken(
                participantId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_BANK")));
    }

    private static RtpRequestResponse response(UUID id) {
        return new RtpRequestResponse(
                id,
                "RTP-CONTROLLER-1",
                "BANK_A",
                "BANK_B",
                "010100000001",
                null,
                new BigDecimal("1000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LAK",
                null,
                RtpStatus.PENDING_AUTH,
                null,
                Instant.parse("2026-06-23T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-22T00:00:00Z"),
                0L);
    }
}
