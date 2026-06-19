package com.example.switching.webhook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.security.util.ApiKeyHashUtil;
import com.example.switching.webhook.crypto.EncryptedSecret;
import com.example.switching.webhook.crypto.SecretEncryptionException;
import com.example.switching.webhook.crypto.SecretEncryptionService;
import com.example.switching.webhook.entity.WebhookDeliveryLogEntity;
import com.example.switching.webhook.entity.WebhookRegistrationEntity;
import com.example.switching.webhook.repository.WebhookDeliveryLogRepository;
import com.example.switching.webhook.repository.WebhookRegistrationRepository;
import com.example.switching.webhook.security.WebhookEndpointPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Core webhook registration, signing, delivery, and retry logic. */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    static final int AUTO_FAIL_THRESHOLD = 10;
    static final int MAX_ATTEMPTS = 5;
    private static final int[] RETRY_DELAYS_SECONDS = {30, 120, 600, 3_600};

    private final WebhookRegistrationRepository registrationRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookHttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;
    private final WebhookEndpointPolicy endpointPolicy;

    public WebhookDeliveryService(WebhookRegistrationRepository registrationRepository,
                                  WebhookDeliveryLogRepository deliveryLogRepository,
                                  WebhookHttpSender httpSender,
                                  ObjectMapper objectMapper,
                                  SecretEncryptionService secretEncryptionService,
                                  WebhookEndpointPolicy endpointPolicy) {
        this.registrationRepository = registrationRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.httpSender = httpSender;
        this.objectMapper = objectMapper;
        this.secretEncryptionService = secretEncryptionService;
        this.endpointPolicy = endpointPolicy;
    }

    @Transactional
    public void deliver(String eventType, String pspId, String eventRef, Map<String, Object> data) {
        if (pspId == null) {
            return;
        }

        List<WebhookRegistrationEntity> registrations =
                registrationRepository.findByPspIdAndStatus(pspId, "ACTIVE");
        if (registrations.isEmpty()) {
            return;
        }

        String payloadJson = toJson(data);
        for (WebhookRegistrationEntity registration : registrations) {
            if (!subscribesTo(registration, eventType)) {
                continue;
            }

            WebhookDeliveryLogEntity logEntry = createPendingLogEntry(
                    registration.getWebhookId(), eventType, eventRef, payloadJson);
            deliveryLogRepository.save(logEntry);
            log.debug("Webhook log created: webhookId={} event={} ref={} logId={}",
                    registration.getWebhookId(), eventType, eventRef, logEntry.getId());
            trySendNow(registration, logEntry, payloadJson);
        }
    }

    @Transactional
    public void retryDelivery(WebhookDeliveryLogEntity logEntry) {
        WebhookRegistrationEntity registration = registrationRepository
                .findByWebhookId(logEntry.getWebhookId())
                .orElse(null);

        if (registration == null
                || "FAILED".equals(registration.getStatus())
                || "PAUSED".equals(registration.getStatus())) {
            logEntry.setStatus("FAILED_FINAL");
            deliveryLogRepository.save(logEntry);
            return;
        }

        trySendNow(registration, logEntry, logEntry.getPayload());
    }

    private void trySendNow(WebhookRegistrationEntity registration,
                            WebhookDeliveryLogEntity logEntry,
                            String payloadJson) {
        logEntry.setAttemptCount(logEntry.getAttemptCount() + 1);
        logEntry.setLastAttemptAt(LocalDateTime.now());

        try {
            SigningSecrets signingSecrets = decryptSigningSecrets(registration);
            int status;
            if (signingSecrets.previous() == null) {
                status = httpSender.send(
                        registration.getUrl(),
                        logEntry.getEventType(),
                        String.valueOf(logEntry.getId()),
                        payloadJson,
                        signingSecrets.current());
            } else {
                status = httpSender.send(
                        registration.getUrl(),
                        logEntry.getEventType(),
                        String.valueOf(logEntry.getId()),
                        payloadJson,
                        signingSecrets.current(),
                        signingSecrets.previous());
            }

            if (status >= 200 && status < 300) {
                logEntry.setStatus("DELIVERED");
                logEntry.setResponseStatus(status);
                logEntry.setDeliveredAt(LocalDateTime.now());
                registration.setLastDeliveredAt(LocalDateTime.now());
                registrationRepository.save(registration);
                log.info("Webhook delivered: webhookId={} event={} status={} secretVersion={}",
                        registration.getWebhookId(),
                        logEntry.getEventType(),
                        status,
                        registration.getSecretVersion());
            } else {
                handleFailure(registration, logEntry, status, "Non-2xx response: " + status);
            }
        } catch (SecretEncryptionException ex) {
            // Fail closed: a KMS/Vault outage never falls back to a hash or plaintext column.
            log.error("Webhook secret unavailable; delivery blocked: webhookId={} event={}",
                    registration.getWebhookId(), logEntry.getEventType());
            handleFailure(registration, logEntry, null, "Signing secret unavailable");
        } catch (WebhookDeliveryException ex) {
            handleFailure(registration, logEntry, null, ex.getMessage());
        }

        deliveryLogRepository.save(logEntry);
    }

    private SigningSecrets decryptSigningSecrets(WebhookRegistrationEntity registration) {
        String current = secretEncryptionService.decrypt(registration.getSecretCiphertext());
        String previous = null;

        if (registration.getPreviousSecretCiphertext() != null) {
            LocalDateTime expiresAt = registration.getPreviousSecretExpiresAt();
            if (expiresAt != null && LocalDateTime.now().isBefore(expiresAt)) {
                previous = secretEncryptionService.decrypt(registration.getPreviousSecretCiphertext());
            } else {
                registration.setPreviousSecretCiphertext(null);
                registration.setPreviousSecretExpiresAt(null);
                registrationRepository.save(registration);
            }
        }
        return new SigningSecrets(current, previous);
    }

    private void handleFailure(WebhookRegistrationEntity registration,
                               WebhookDeliveryLogEntity logEntry,
                               Integer responseStatus,
                               String reason) {
        if (responseStatus != null) {
            logEntry.setResponseStatus(responseStatus);
        }

        int attempt = logEntry.getAttemptCount();
        if (attempt >= MAX_ATTEMPTS) {
            logEntry.setStatus("FAILED_FINAL");
            log.warn("Webhook permanently failed: webhookId={} event={} attempts={}",
                    registration.getWebhookId(), logEntry.getEventType(), attempt);
        } else {
            int delaySeconds = RETRY_DELAYS_SECONDS[
                    Math.min(attempt - 1, RETRY_DELAYS_SECONDS.length - 1)];
            logEntry.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.debug("Webhook attempt {} failed, next retry in {}s: webhookId={} event={} reason={}",
                    attempt,
                    delaySeconds,
                    registration.getWebhookId(),
                    logEntry.getEventType(),
                    reason);
        }

        registrationRepository.incrementFailedDeliveries(
                registration.getWebhookId(), AUTO_FAIL_THRESHOLD);
    }

    private WebhookDeliveryLogEntity createPendingLogEntry(String webhookId,
                                                           String eventType,
                                                           String eventRef,
                                                           String payloadJson) {
        WebhookDeliveryLogEntity entry = new WebhookDeliveryLogEntity();
        entry.setWebhookId(webhookId);
        entry.setEventType(eventType);
        entry.setEventRef(eventRef);
        entry.setPayload(payloadJson);
        entry.setStatus("PENDING");
        return entry;
    }

    private boolean subscribesTo(WebhookRegistrationEntity registration, String eventType) {
        String types = registration.getEventTypes();
        return types != null
                && (types.contains("\"*\"") || types.contains("\"" + eventType + "\""));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize webhook payload: {}", ex.getMessage());
            return "{}";
        }
    }

    /** Registers a webhook while persisting only envelope ciphertext and a one-way hash. */
    @Transactional
    public WebhookRegistrationEntity register(String pspId,
                                              String url,
                                              List<String> eventTypes,
                                              String signingSecret) {
        endpointPolicy.validate(url);

        String eventTypesJson;
        try {
            eventTypesJson = objectMapper.writeValueAsString(eventTypes);
        } catch (JsonProcessingException ex) {
            eventTypesJson = "[\"*\"]";
        }

        EncryptedSecret encryptedSecret = secretEncryptionService.encrypt(signingSecret);
        WebhookRegistrationEntity entity = new WebhookRegistrationEntity();
        entity.setWebhookId(UUID.randomUUID().toString());
        entity.setPspId(pspId);
        entity.setUrl(url);
        entity.setEventTypes(eventTypesJson);
        entity.setSecretCiphertext(encryptedSecret.ciphertext());
        entity.setSecretKeyId(encryptedSecret.keyId());
        entity.setSecretVersion(encryptedSecret.version());
        entity.setSecretHash(ApiKeyHashUtil.hash(signingSecret));
        entity.setStatus("ACTIVE");
        return registrationRepository.save(entity);
    }

    @Transactional
    public void pause(String webhookId) {
        registrationRepository.findByWebhookId(webhookId).ifPresent(registration -> {
            registration.setStatus("PAUSED");
            registrationRepository.save(registration);
        });
    }

    private record SigningSecrets(String current, String previous) {
    }
}
