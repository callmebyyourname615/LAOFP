package com.example.switching.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.inquiry.dto.CreateInquiryRequest;
import com.example.switching.inquiry.dto.CreateInquiryResponse;
import com.example.switching.inquiry.entity.InquiryEntity;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.inquiry.repository.InquiryStatusHistoryRepository;
import com.example.switching.inquiry.service.AccountLookupService;
import com.example.switching.inquiry.service.AccountLookupService.AccountLookupResult;
import com.example.switching.inquiry.service.CreateInquiryService;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.service.ParticipantService;
import com.example.switching.routing.dto.RoutingResolveResponse;
import com.example.switching.routing.service.RoutingService;
import com.example.switching.transfer.exception.InquiryValidationException;

/**
 * Unit tests for {@link CreateInquiryService} (TC-INQUIRY-001 – 007).
 *
 * Pure Mockito — no Spring context, no database.
 *
 * Tests:
 * - Happy path: ELIGIBLE response when bank active + account found
 * - Missing sourceBank → InquiryValidationException
 * - Missing creditorAccount → InquiryValidationException
 * - Source bank suspended → InquiryValidationException
 * - Unknown destination bank → NOT_ELIGIBLE
 * - Destination bank suspended → NOT_ELIGIBLE
 * - Account lookup fails → NOT_ELIGIBLE
 */
@ExtendWith(MockitoExtension.class)
class CreateInquiryServiceTest {

    @Mock InquiryRepository              inquiryRepo;
    @Mock InquiryStatusHistoryRepository historyRepo;
    @Mock AuditLogService                auditLogService;
    @Mock ParticipantService             participantService;
    @Mock RoutingService                 routingService;
    @Mock AccountLookupService           accountLookupService;

    CreateInquiryService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ParticipantEntity activeParticipant(String bankCode) {
        ParticipantEntity p = new ParticipantEntity();
        p.setBankCode(bankCode);
        p.setStatus(com.example.switching.participant.enums.ParticipantStatus.ACTIVE);
        return p;
    }

    private static ParticipantEntity suspendedParticipant(String bankCode) {
        ParticipantEntity p = new ParticipantEntity();
        p.setBankCode(bankCode);
        p.setStatus(com.example.switching.participant.enums.ParticipantStatus.INACTIVE);
        return p;
    }

    private static CreateInquiryRequest validRequest() {
        CreateInquiryRequest r = new CreateInquiryRequest();
        r.setSourceBank("BANK_A");
        r.setDestinationBank("BANK_B");
        r.setCreditorAccount("010200000001");
        r.setCurrency("LAK");
        r.setAmount(new java.math.BigDecimal("150000.00"));
        r.setClientInquiryId("CLIENT-001");
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new CreateInquiryService(
                inquiryRepo, historyRepo, auditLogService,
                participantService, routingService, accountLookupService);

        // participantService.normalize() returns the bank code as-is
        lenient().when(participantService.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // historyRepo.save() — lenient stub (we don't assert on it)
        lenient().when(historyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // inquiryRepo.save() — returns the entity unchanged
        lenient().when(inquiryRepo.save(any(InquiryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── TC-INQUIRY-001 — happy path ELIGIBLE ──────────────────────────────────

    @Test
    void create_validRequest_returnsEligible() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(activeParticipant("BANK_A"));
        when(participantService.findByBankCode("BANK_B"))
                .thenReturn(activeParticipant("BANK_B"));
        when(accountLookupService.lookup("BANK_B", "010200000001"))
                .thenReturn(AccountLookupResult.found("Test Account Name"));

        RoutingResolveResponse routing = new RoutingResolveResponse();
        routing.setRouteCode("ROUTE_A_TO_B");
        routing.setConnectorName("MOCK_BANK_B_CONNECTOR");
        when(routingService.resolve(anyString(), anyString(), anyString()))
                .thenReturn(routing);

        CreateInquiryResponse resp = service.create(validRequest());

        assertNotNull(resp.getInquiryRef(),  "inquiryRef must be set");
        assertEquals("ELIGIBLE", resp.getStatus());
        assertTrue(Boolean.TRUE.equals(resp.getEligibleForTransfer()));
        assertEquals("Test Account Name", resp.getDestinationAccountName());
    }

    // ── TC-INQUIRY-002 — missing sourceBank ───────────────────────────────────

    @Test
    void create_missingSouceBank_throwsInquiryValidation() {
        CreateInquiryRequest req = validRequest();
        req.setSourceBank(null);

        assertThrows(InquiryValidationException.class,
                () -> service.create(req),
                "null sourceBank must throw InquiryValidationException");
    }

    // ── TC-INQUIRY-003 — missing creditorAccount ──────────────────────────────

    @Test
    void create_missingCreditorAccount_throwsInquiryValidation() {
        // Service validates creditorAccount BEFORE calling participantService — no stub needed
        CreateInquiryRequest req = validRequest();
        req.setCreditorAccount(null);

        assertThrows(InquiryValidationException.class,
                () -> service.create(req),
                "null creditorAccount must throw InquiryValidationException");
    }

    // ── TC-INQUIRY-004 — source bank SUSPENDED ────────────────────────────────

    @Test
    void create_sourceBankSuspended_throwsInquiryValidation() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(suspendedParticipant("BANK_A"));

        InquiryValidationException ex = assertThrows(InquiryValidationException.class,
                () -> service.create(validRequest()));
        assertTrue(ex.getMessage().contains("not ACTIVE"),
                "Error must mention 'not ACTIVE'");
    }

    // ── TC-INQUIRY-005 — unknown destination bank ─────────────────────────────

    @Test
    void create_unknownDestinationBank_returnsNotEligible() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(activeParticipant("BANK_A"));
        when(participantService.findByBankCode("BANK_B"))
                .thenThrow(new ParticipantNotFoundException("BANK_B"));

        CreateInquiryResponse resp = service.create(validRequest());

        assertEquals("NOT_ELIGIBLE", resp.getStatus());
        assertFalse(Boolean.TRUE.equals(resp.getEligibleForTransfer()));
        assertTrue(resp.getMessage().contains("not eligible"),
                "Message must contain 'not eligible'");
    }

    // ── TC-INQUIRY-006 — destination bank SUSPENDED ───────────────────────────

    @Test
    void create_destinationBankSuspended_returnsNotEligible() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(activeParticipant("BANK_A"));
        when(participantService.findByBankCode("BANK_B"))
                .thenReturn(suspendedParticipant("BANK_B"));

        CreateInquiryResponse resp = service.create(validRequest());

        assertEquals("NOT_ELIGIBLE", resp.getStatus());
        assertFalse(Boolean.TRUE.equals(resp.getEligibleForTransfer()));
    }

    // ── TC-INQUIRY-007 — account not found → NOT_ELIGIBLE ────────────────────

    @Test
    void create_accountNotFound_returnsNotEligible() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(activeParticipant("BANK_A"));
        when(participantService.findByBankCode("BANK_B"))
                .thenReturn(activeParticipant("BANK_B"));
        when(accountLookupService.lookup(eq("BANK_B"), anyString()))
                .thenReturn(AccountLookupResult.notFound("Account does not exist"));

        CreateInquiryResponse resp = service.create(validRequest());

        assertEquals("NOT_ELIGIBLE", resp.getStatus());
        assertFalse(Boolean.TRUE.equals(resp.getEligibleForTransfer()));
        assertFalse(Boolean.TRUE.equals(resp.getAccountFound()));
    }

    // ── TC-INQUIRY-008 — inquiryRef format ────────────────────────────────────

    @Test
    void create_eligibleRequest_inquiryRefHasCorrectFormat() {
        when(participantService.findByBankCode("BANK_A"))
                .thenReturn(activeParticipant("BANK_A"));
        when(participantService.findByBankCode("BANK_B"))
                .thenReturn(activeParticipant("BANK_B"));
        when(accountLookupService.lookup(anyString(), anyString()))
                .thenReturn(AccountLookupResult.found("Customer Name"));
        lenient().when(routingService.resolve(anyString(), anyString(), anyString()))
                .thenReturn(new RoutingResolveResponse());

        CreateInquiryResponse resp = service.create(validRequest());

        assertNotNull(resp.getInquiryRef());
        assertTrue(resp.getInquiryRef().startsWith("INQ-"),
                "inquiryRef must start with 'INQ-'");
        assertTrue(resp.getInquiryRef().length() > 10,
                "inquiryRef must have a timestamp+suffix portion");
    }
}
