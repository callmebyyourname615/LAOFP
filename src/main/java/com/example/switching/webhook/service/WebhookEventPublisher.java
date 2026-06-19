package com.example.switching.webhook.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fire-and-quiet webhook event publisher.
 *
 * <p>Injected into {@link com.example.switching.transfer.service.CreateTransferService}
 * and {@link com.example.switching.outbox.service.OutboxProcessorService} so that
 * webhook delivery never blocks or fails the core payment transaction.
 *
 * <p>All exceptions are caught and logged as WARN — identical pattern to
 * {@link com.example.switching.transfer.service.TransactionEventPublisher}.
 *
 * <h3>Standard event payload envelope</h3>
 * <pre>
 * {
 *   "event"     : "TRANSFER.SETTLED",
 *   "timestamp" : "2026-05-22T10:00:00Z",
 *   "data"      : { ... domain fields ... }
 * }
 * </pre>
 */
@Component
public class WebhookEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventPublisher.class);

    private final WebhookDeliveryService deliveryService;

    public WebhookEventPublisher(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * Publish an event to all ACTIVE webhook registrations for the given PSP.
     *
     * @param eventType  e.g. {@code "TRANSFER.SETTLED"}
     * @param pspId      the participant (bank_code) to notify
     * @param eventRef   correlation reference (transfer_ref, inquiry_ref, etc.)
     * @param data       domain-specific payload fields
     */
    public void publishQuietly(String eventType,
                               String pspId,
                               String eventRef,
                               Map<String, Object> data) {
        if (pspId == null) return;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event",     eventType);
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("ref",       eventRef);
            envelope.put("data",      data);
            deliveryService.deliver(eventType, pspId, eventRef, envelope);
        } catch (Exception ex) {
            log.warn("Webhook publish failed (event={} psp={} ref={}): {}",
                    eventType, pspId, eventRef, ex.getMessage(), ex);
        }
    }

    // ── Convenience methods for standard payment lifecycle events ───────────

    public void transferInitiated(String transferRef, String sourcePspId, Map<String, Object> data) {
        publishQuietly("TRANSFER.INITIATED", sourcePspId, transferRef, data);
    }

    public void transferSettled(String transferRef, String sourcePspId, Map<String, Object> data) {
        publishQuietly("TRANSFER.SETTLED", sourcePspId, transferRef, data);
    }

    public void transferRejected(String transferRef, String sourcePspId, Map<String, Object> data) {
        publishQuietly("TRANSFER.REJECTED", sourcePspId, transferRef, data);
    }

    public void transferRetryScheduled(String transferRef, String sourcePspId, Map<String, Object> data) {
        publishQuietly("TRANSFER.RETRY_SCHEDULED", sourcePspId, transferRef, data);
    }

    public void transferBlocked(String transferRef, String sourcePspId, Map<String, Object> data) {
        publishQuietly("TRANSFER.BLOCKED", sourcePspId, transferRef, data);
    }

    public void liquidityLowAlert(String pspId, Map<String, Object> data) {
        publishQuietly("LIQUIDITY.LOW_ALERT", pspId, pspId, data);
    }

    public void settlementCycleCompleted(String cycleRef, String pspId, Map<String, Object> data) {
        publishQuietly("SETTLEMENT.CYCLE.COMPLETED", pspId, cycleRef, data);
    }
}
