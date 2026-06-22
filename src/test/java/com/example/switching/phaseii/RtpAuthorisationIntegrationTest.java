package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.rtp.dto.AuthoriseRtpRequest;
import com.example.switching.rtp.entity.RtpRequestEntity;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.repository.RtpAuthorisationRepository;
import com.example.switching.rtp.repository.RtpInstallmentScheduleRepository;
import com.example.switching.rtp.repository.RtpRequestRepository;
import com.example.switching.rtp.repository.RtpStateTransitionRepository;
import com.example.switching.rtp.service.RtpActor;
import com.example.switching.rtp.service.RtpAuthorisationService;
import com.example.switching.rtp.service.RtpDomainEventPublisher;
import com.example.switching.rtp.service.RtpSettlementGateway;
import com.example.switching.rtp.service.RtpStateMachine;

class RtpAuthorisationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RtpRequestRepository requests;

    @Autowired
    private RtpAuthorisationRepository authorisations;

    @Autowired
    private RtpInstallmentScheduleRepository installments;

    @Autowired
    private RtpStateTransitionRepository transitions;

    @BeforeEach
    void cleanRtp() {
        transitions.deleteAllInBatch();
        installments.deleteAllInBatch();
        authorisations.deleteAllInBatch();
        requests.deleteAllInBatch();
    }

    @Test
    void fullAuthorisationSettlesThroughExistingTransferGateway() {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        RtpRequestEntity request = new RtpRequestEntity();
        request.setId(UUID.randomUUID());
        request.setRequestCorrelationId("RTP-AUTH-001");
        request.setRequestFingerprint("a".repeat(64));
        request.setPayeeParticipantId("BANK_A");
        request.setPayerParticipantId("BANK_B");
        request.setPayeeAccount("PAYEE-001");
        request.setPayerAccount("PAYER-001");
        request.setRequestedAmount(new BigDecimal("1000.0000"));
        request.setAuthorisedAmount(BigDecimal.ZERO);
        request.setSettledAmount(BigDecimal.ZERO);
        request.setCurrency("LAK");
        request.setStatus(RtpStatus.PENDING_AUTH);
        request.setExpiresAt(now.plusSeconds(3600));
        requests.saveAndFlush(request);

        RtpSettlementGateway gateway = mock(RtpSettlementGateway.class);
        when(gateway.submit(any())).thenReturn(
                new RtpSettlementGateway.SettlementSubmission(
                        "TXN-RTP-001", "SETTLED", "SETTLED"));
        RtpDomainEventPublisher events = mock(RtpDomainEventPublisher.class);
        RtpAuthorisationService service = new RtpAuthorisationService(
                requests,
                authorisations,
                installments,
                transitions,
                new RtpStateMachine(),
                gateway,
                events,
                Clock.fixed(now, ZoneOffset.UTC));

        var response = service.authorise(
                request.getId(),
                new AuthoriseRtpRequest(
                        "AUTH-RTP-001",
                        RtpAuthorisationMode.FULL,
                        new BigDecimal("1000.0000"),
                        "INQ-RTP-001",
                        List.of()),
                new RtpActor("payer-user", "BANK_B", false));

        assertEquals(RtpStatus.SETTLED, response.status());
        assertEquals("TXN-RTP-001", response.transferReference());
        assertEquals(new BigDecimal("1000.0000"), response.authorisedAmount());
        verify(gateway).submit(any());
    }
}
