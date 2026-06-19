package com.example.switching.webhook.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.webhook.dto.WebhookRegisterRequest;
import com.example.switching.webhook.dto.WebhookResponse;
import com.example.switching.webhook.dto.WebhookSecretRotateRequest;
import com.example.switching.webhook.dto.WebhookSecretRotationResponse;
import com.example.switching.webhook.entity.WebhookRegistrationEntity;
import com.example.switching.webhook.repository.WebhookDeliveryLogRepository;
import com.example.switching.webhook.repository.WebhookRegistrationRepository;
import com.example.switching.webhook.service.WebhookDeliveryService;
import com.example.switching.webhook.service.WebhookEventPublisher;
import com.example.switching.webhook.service.WebhookSecretRotationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Webhook management endpoints for PSP callers.
 *
 * <pre>
 *  POST   /v1/webhooks                — register a new endpoint
 *  GET    /v1/webhooks                — list all endpoints for calling PSP
 *  GET    /v1/webhooks/{webhookId}    — endpoint detail + recent delivery stats
 *  DELETE /v1/webhooks/{webhookId}    — pause endpoint (status → PAUSED)
 *  POST   /v1/webhooks/{webhookId}/test — fire TEST.PING delivery
 *  POST   /v1/webhooks/{webhookId}/secret/rotate — rotate signing secret with grace period
 * </pre>
 *
 * <p>BANK callers see only their own registrations (scoped by pspId from auth context).
 * ADMIN callers may use the {@code X-Override-Psp} header to manage any PSP's webhooks.
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/webhooks")
public class WebhookController {

    private final WebhookDeliveryService        deliveryService;
    private final WebhookEventPublisher         eventPublisher;
    private final WebhookRegistrationRepository registrationRepository;
    private final WebhookDeliveryLogRepository  deliveryLogRepository;
    private final WebhookSecretRotationService  rotationService;
    private final ObjectMapper                  objectMapper;

    public WebhookController(WebhookDeliveryService deliveryService,
                             WebhookEventPublisher eventPublisher,
                             WebhookRegistrationRepository registrationRepository,
                             WebhookDeliveryLogRepository deliveryLogRepository,
                             WebhookSecretRotationService rotationService,
                             ObjectMapper objectMapper) {
        this.deliveryService        = deliveryService;
        this.eventPublisher         = eventPublisher;
        this.registrationRepository = registrationRepository;
        this.deliveryLogRepository  = deliveryLogRepository;
        this.rotationService        = rotationService;
        this.objectMapper           = objectMapper;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * Register a new webhook endpoint.
     *
     * <p>The {@code signingSecret} is echoed back once in this response and is
     * never retrievable again. The database stores envelope ciphertext and a SHA-256 hash, never plaintext.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody WebhookRegisterRequest request,
            Authentication auth) {

        String pspId = resolvedPspId(auth);

        WebhookRegistrationEntity entity = deliveryService.register(
                pspId,
                request.getUrl(),
                request.getEventTypes(),
                request.getSigningSecret());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "webhookId",    entity.getWebhookId(),
                "pspId",        entity.getPspId(),
                "url",          entity.getUrl(),
                "eventTypes",   request.getEventTypes(),
                "status",       entity.getStatus(),
                "signingSecret", request.getSigningSecret(),  // returned once only
                "message",      "Store the signingSecret securely — it cannot be retrieved again"
        ));
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<WebhookResponse>> list(Authentication auth) {
        String pspId = resolvedPspId(auth);
        List<WebhookResponse> result = registrationRepository
                .findByPspIdOrderByCreatedAtDesc(pspId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Detail ───────────────────────────────────────────────────────────────

    @GetMapping("/{webhookId}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable String webhookId,
            Authentication auth) {

        return registrationRepository.findByWebhookId(webhookId)
                .filter(r -> isOwnerOrAdmin(r.getPspId(), auth))
                .map(reg -> {
                    var recentDeliveries = deliveryLogRepository
                            .findByWebhookIdOrderByCreatedAtDesc(webhookId)
                            .stream()
                            .limit(20)
                            .map(d -> Map.of(
                                    "id",             d.getId(),
                                    "eventType",      d.getEventType(),
                                    "status",         d.getStatus(),
                                    "attemptCount",   d.getAttemptCount(),
                                    "responseStatus", d.getResponseStatus() != null ? d.getResponseStatus() : "-",
                                    "createdAt",      d.getCreatedAt()
                            ))
                            .toList();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "registration",     toResponse(reg),
                            "recentDeliveries", recentDeliveries
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Pause / Delete ────────────────────────────────────────────────────────

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<Void> pause(@PathVariable String webhookId, Authentication auth) {
        registrationRepository.findByWebhookId(webhookId)
                .filter(r -> isOwnerOrAdmin(r.getPspId(), auth))
                .ifPresent(r -> deliveryService.pause(webhookId));
        return ResponseEntity.noContent().build();
    }

    // ── Signing-secret rotation ───────────────────────────────────────────────

    @PostMapping("/{webhookId}/secret/rotate")
    public ResponseEntity<WebhookSecretRotationResponse> rotateSecret(
            @PathVariable String webhookId,
            @Valid @RequestBody WebhookSecretRotateRequest request,
            Authentication auth) {

        return registrationRepository.findByWebhookId(webhookId)
                .filter(registration -> isOwnerOrAdmin(registration.getPspId(), auth))
                .map(registration -> ResponseEntity.ok(rotationService.rotate(
                        webhookId,
                        request.getSigningSecret(),
                        request.getGraceMinutes(),
                        auth.getName())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Test ping ─────────────────────────────────────────────────────────────

    /**
     * Fire a {@code TEST.PING} event to this webhook endpoint.
     * Useful for PSPs to verify their endpoint is reachable.
     */
    @PostMapping("/{webhookId}/test")
    public ResponseEntity<Map<String, Object>> testPing(
            @PathVariable String webhookId,
            Authentication auth) {

        return registrationRepository.findByWebhookId(webhookId)
                .filter(r -> isOwnerOrAdmin(r.getPspId(), auth))
                .map(reg -> {
                    eventPublisher.publishQuietly(
                            "TEST.PING",
                            reg.getPspId(),
                            "TEST-" + webhookId,
                            Map.of("webhookId", webhookId, "message", "Test ping from switching server"));
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "webhookId", webhookId,
                            "event",     "TEST.PING",
                            "status",    "QUEUED"
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolvedPspId(Authentication auth) {
        if (auth.getDetails() instanceof Map<?, ?> details) {
            Object bankCode = details.get("bankCode");
            if (bankCode instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return auth.getName();
    }

    private boolean isOwnerOrAdmin(String pspId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return isAdmin || pspId.equals(resolvedPspId(auth));
    }

    private WebhookResponse toResponse(WebhookRegistrationEntity reg) {
        List<String> types = parseEventTypes(reg.getEventTypes());
        return new WebhookResponse(
                reg.getWebhookId(),
                reg.getPspId(),
                reg.getUrl(),
                types,
                reg.getStatus(),
                reg.getFailedDeliveries(),
                reg.getLastDeliveredAt(),
                reg.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private List<String> parseEventTypes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return Arrays.asList(json.replace("[", "").replace("]", "")
                    .replace("\"", "").split(","));
        }
    }
}
