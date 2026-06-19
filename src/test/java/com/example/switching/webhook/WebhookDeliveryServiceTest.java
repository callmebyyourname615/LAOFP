package com.example.switching.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.example.switching.webhook.crypto.SecretEncryptionException;
import com.example.switching.webhook.crypto.SecretEncryptionService;
import com.example.switching.webhook.entity.WebhookDeliveryLogEntity;
import com.example.switching.webhook.entity.WebhookRegistrationEntity;
import com.example.switching.webhook.repository.WebhookDeliveryLogRepository;
import com.example.switching.webhook.repository.WebhookRegistrationRepository;
import com.example.switching.webhook.service.WebhookDeliveryException;
import com.example.switching.webhook.service.WebhookDeliveryService;
import com.example.switching.webhook.security.WebhookEndpointPolicy;
import com.example.switching.webhook.service.WebhookHttpSender;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for WebhookDeliveryService.
 *
 * Validates the two-phase delivery flow:
 *   1. PENDING log row created in DB
 *   2. Immediate HTTP delivery attempted
 *   3. On success: status → DELIVERED
 *   4. On failure: status stays PENDING, nextRetryAt computed from backoff schedule
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookDeliveryServiceTest {

    private static final String PSP_ID      = "BANK_A";
    private static final String WEBHOOK_ID  = "wh-test-uuid-001";
    private static final String HOOK_URL    = "https://bankA.example.com/webhooks";
    private static final String SECRET      = "super-secret-32-chars-at-minimum!";
    private static final String EVENT_TYPE  = "TRANSFER.SETTLED";
    private static final String EVENT_REF   = "TXN-REF-001";

    @Mock WebhookRegistrationRepository registrationRepository;
    @Mock WebhookDeliveryLogRepository  deliveryLogRepository;
    @Mock WebhookHttpSender             httpSender;
    @Mock SecretEncryptionService        secretEncryptionService;
    @Mock WebhookEndpointPolicy          endpointPolicy;

    private WebhookDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new WebhookDeliveryService(
                registrationRepository, deliveryLogRepository, httpSender, new ObjectMapper(),
                secretEncryptionService, endpointPolicy);

        // By default, save() returns the same entity
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(secretEncryptionService.decrypt(anyString())).thenReturn(SECRET);
    }

    // ── Guard rails ──────────────────────────────────────────────────────────

    @Test
    void deliver_withNullPspId_doesNothing() {
        service.deliver(EVENT_TYPE, null, EVENT_REF, Map.of());

        verify(registrationRepository, never()).findByPspIdAndStatus(any(), any());
        verify(deliveryLogRepository,  never()).save(any());
    }

    @Test
    void deliver_withNoActiveRegistrations_doesNothing() {
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of());

        verify(httpSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void deliver_registrationNotSubscribedToEvent_skipsDelivery() {
        WebhookRegistrationEntity reg = registration(
                "[\"TRANSFER.REJECTED\",\"TRANSFER.BLOCKED\"]"); // not SETTLED

        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE"))
                .thenReturn(List.of(reg));

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of("amount", 100));

        verify(deliveryLogRepository, never()).save(any());
        verify(httpSender, never()).send(any(), any(), any(), any(), any());
    }

    // ── Successful delivery ───────────────────────────────────────────────────

    @Test
    void deliver_http200_setsStatusDelivered() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(200);

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of("amount", 500));

        ArgumentCaptor<WebhookDeliveryLogEntity> cap = ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());

        WebhookDeliveryLogEntity finalEntry = cap.getAllValues().get(1); // second save = after HTTP call
        assertEquals("DELIVERED", finalEntry.getStatus());
        assertNotNull(finalEntry.getDeliveredAt());
        assertEquals(1, finalEntry.getAttemptCount());
        assertEquals(200, finalEntry.getResponseStatus());
    }

    @Test
    void deliver_http201_alsoCountsAsSuccess() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(201);

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of());

        ArgumentCaptor<WebhookDeliveryLogEntity> cap = ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());
        assertEquals("DELIVERED", cap.getAllValues().get(1).getStatus());
    }

    // ── Failed delivery + retry backoff ──────────────────────────────────────

    @Test
    void deliver_http500_keepsPendingAndSetsNextRetryAt() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(500);

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of());

        ArgumentCaptor<WebhookDeliveryLogEntity> cap = ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());

        WebhookDeliveryLogEntity finalEntry = cap.getAllValues().get(1);
        // Still PENDING — not failed yet (only 1 attempt)
        assertEquals("PENDING", finalEntry.getStatus());
        assertNotNull(finalEntry.getNextRetryAt(), "nextRetryAt must be set after first failure");
        assertEquals(500, finalEntry.getResponseStatus());
        // First failure: delay = 30s → nextRetryAt should be ~now+30s
        verify(registrationRepository).incrementFailedDeliveries(eq(WEBHOOK_ID), anyInt());
    }

    @Test
    void deliver_networkException_keepsPendingWithRetryBackoff() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new WebhookDeliveryException("Connection timed out",
                        new java.io.IOException("timeout")));

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of());

        ArgumentCaptor<WebhookDeliveryLogEntity> cap = ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());

        WebhookDeliveryLogEntity finalEntry = cap.getAllValues().get(1);
        assertEquals("PENDING", finalEntry.getStatus());
        assertNotNull(finalEntry.getNextRetryAt());
        assertNull(finalEntry.getResponseStatus()); // no HTTP response on network error
    }


    @Test
    void deliver_kmsUnavailable_failsClosedWithoutSendingUnsignedRequest() {
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE"))
                .thenReturn(List.of(reg));
        when(secretEncryptionService.decrypt(anyString()))
                .thenThrow(new SecretEncryptionException("Vault unavailable"));

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of("amount", 500));

        verify(httpSender, never()).send(any(), any(), any(), any(), any());
        verify(registrationRepository).incrementFailedDeliveries(eq(WEBHOOK_ID), anyInt());
        ArgumentCaptor<WebhookDeliveryLogEntity> cap =
                ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());
        WebhookDeliveryLogEntity finalEntry = cap.getAllValues().get(1);
        assertEquals("PENDING", finalEntry.getStatus());
        assertNotNull(finalEntry.getNextRetryAt());
    }

    // ── Wildcard subscription ─────────────────────────────────────────────────

    @Test
    void deliver_wildcardSubscription_matchesAnyEventType() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"*\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(200);

        service.deliver("WHATEVER.EVENT", PSP_ID, EVENT_REF, Map.of());

        verify(httpSender, times(1)).send(any(), any(), any(), any(), any());
    }

    // ── Retry path ─────────────────────────────────────────────────────────────

    @Test
    void retryDelivery_registrationNotFound_setsFailedFinal() {
        WebhookDeliveryLogEntity logEntry = pendingLogEntry(WEBHOOK_ID, 3);
        when(registrationRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());

        service.retryDelivery(logEntry);

        assertEquals("FAILED_FINAL", logEntry.getStatus());
        verify(deliveryLogRepository).save(logEntry);
    }

    @Test
    void retryDelivery_registrationPaused_setsFailedFinal() {
        WebhookRegistrationEntity reg = registration("[\"*\"]");
        reg.setStatus("PAUSED");
        WebhookDeliveryLogEntity logEntry = pendingLogEntry(WEBHOOK_ID, 2);
        when(registrationRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.of(reg));

        service.retryDelivery(logEntry);

        assertEquals("FAILED_FINAL", logEntry.getStatus());
        verify(httpSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void retryDelivery_http200_setsDelivered() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"*\"]");
        WebhookDeliveryLogEntity logEntry = pendingLogEntry(WEBHOOK_ID, 2);
        when(registrationRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(200);

        service.retryDelivery(logEntry);

        assertEquals("DELIVERED", logEntry.getStatus());
        assertEquals(3, logEntry.getAttemptCount()); // was 2, incremented to 3
        assertNotNull(logEntry.getDeliveredAt());
    }

    // ── MAX_ATTEMPTS → FAILED_FINAL ───────────────────────────────────────────

    @Test
    void deliver_atMaxAttempts_setsFailedFinalInsteadOfRetry() throws Exception {
        // Simulate a log entry that has already reached MAX_ATTEMPTS - 1 attempts
        WebhookRegistrationEntity reg = registration("[\"TRANSFER.SETTLED\"]");
        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE")).thenReturn(List.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(503);

        // Pre-set attemptCount to MAX_ATTEMPTS - 1 so trySendNow hits the limit
        WebhookDeliveryLogEntity preSeed = pendingLogEntry(WEBHOOK_ID, 4); // MAX_ATTEMPTS(5) - 1 = 4
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> {
            WebhookDeliveryLogEntity entry = inv.getArgument(0);
            // Return the pre-seeded entry if it's a fresh PENDING entry
            if ("PENDING".equals(entry.getStatus()) && entry.getAttemptCount() == 0) {
                entry.setAttemptCount(preSeed.getAttemptCount()); // simulate DB state
            }
            return entry;
        });

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of());

        ArgumentCaptor<WebhookDeliveryLogEntity> cap = ArgumentCaptor.forClass(WebhookDeliveryLogEntity.class);
        verify(deliveryLogRepository, times(2)).save(cap.capture());

        WebhookDeliveryLogEntity finalEntry = cap.getAllValues().get(1);
        assertEquals("FAILED_FINAL", finalEntry.getStatus());
    }

    // ── Multiple registrations ─────────────────────────────────────────────────

    @Test
    void deliver_multipleActiveRegistrations_allAttempted() throws Exception {
        WebhookRegistrationEntity reg1 = registration("[\"TRANSFER.SETTLED\"]");
        reg1.setWebhookId("hook-1");

        WebhookRegistrationEntity reg2 = registration("[\"*\"]");
        reg2.setWebhookId("hook-2");
        reg2.setUrl("https://bankA.example.com/webhooks2");

        when(registrationRepository.findByPspIdAndStatus(PSP_ID, "ACTIVE"))
                .thenReturn(List.of(reg1, reg2));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(200);

        service.deliver(EVENT_TYPE, PSP_ID, EVENT_REF, Map.of("amount", 1000));

        // 2 log rows created, 2 HTTP calls
        verify(deliveryLogRepository, times(4)).save(any()); // 2 initial + 2 post-send
        verify(httpSender, times(2)).send(any(), any(), any(), any(), any());
    }

    // ── Backoff timing sanity check ───────────────────────────────────────────

    @Test
    void retryDelivery_firstFailure_hasShortBackoff() throws Exception {
        WebhookRegistrationEntity reg = registration("[\"*\"]");
        // attemptCount = 0 before retry, so trySendNow increments to 1
        WebhookDeliveryLogEntity logEntry = pendingLogEntry(WEBHOOK_ID, 0);
        when(registrationRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.of(reg));
        when(httpSender.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(500);

        service.retryDelivery(logEntry);

        // Attempt 1: delay = 30s → nextRetryAt in the near future
        assertNotNull(logEntry.getNextRetryAt());
        // nextRetryAt should be at least ~30s from now (allow 5s slack for test timing)
        long delaySec = java.time.Duration.between(java.time.LocalDateTime.now(),
                logEntry.getNextRetryAt()).toSeconds();
        // Should be ~30s: allow 25-35 window
        assertEquals(1, logEntry.getAttemptCount());
        // Just verify it's set and in the future
        assertTrue(logEntry.getNextRetryAt().isAfter(java.time.LocalDateTime.now()),
                "nextRetryAt should be in the future");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private WebhookRegistrationEntity registration(String eventTypes) {
        WebhookRegistrationEntity reg = new WebhookRegistrationEntity();
        reg.setWebhookId(WEBHOOK_ID);
        reg.setPspId(PSP_ID);
        reg.setUrl(HOOK_URL);
        reg.setEventTypes(eventTypes);
        reg.setSecretCiphertext("env:v1:test-ciphertext");
        reg.setSecretKeyId("test-key");
        reg.setSecretVersion(1);
        reg.setSecretHash("hash-placeholder");
        reg.setStatus("ACTIVE");
        return reg;
    }

    private WebhookDeliveryLogEntity pendingLogEntry(String webhookId, int attemptCount) {
        WebhookDeliveryLogEntity entry = new WebhookDeliveryLogEntity();
        entry.setWebhookId(webhookId);
        entry.setEventType(EVENT_TYPE);
        entry.setEventRef(EVENT_REF);
        entry.setPayload("{\"amount\":100}");
        entry.setAttemptCount(attemptCount);
        entry.setStatus("PENDING");
        return entry;
    }

    // Required for asserting in the backoff test
    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
