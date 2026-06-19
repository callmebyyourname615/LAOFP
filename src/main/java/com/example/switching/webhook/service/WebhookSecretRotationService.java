package com.example.switching.webhook.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.security.util.ApiKeyHashUtil;
import com.example.switching.webhook.crypto.EncryptedSecret;
import com.example.switching.webhook.crypto.SecretEncryptionService;
import com.example.switching.webhook.dto.WebhookSecretRotationResponse;
import com.example.switching.webhook.entity.WebhookRegistrationEntity;
import com.example.switching.webhook.repository.WebhookRegistrationRepository;

@Service
public class WebhookSecretRotationService {

    private final WebhookRegistrationRepository registrationRepository;
    private final SecretEncryptionService secretEncryptionService;
    private final AuditLogService auditLogService;
    private final int defaultGraceMinutes;
    private final int maxGraceMinutes;

    public WebhookSecretRotationService(
            WebhookRegistrationRepository registrationRepository,
            SecretEncryptionService secretEncryptionService,
            AuditLogService auditLogService,
            @Value("${switching.webhook.rotation.default-grace-minutes:60}") int defaultGraceMinutes,
            @Value("${switching.webhook.rotation.max-grace-minutes:10080}") int maxGraceMinutes) {
        this.registrationRepository = registrationRepository;
        this.secretEncryptionService = secretEncryptionService;
        this.auditLogService = auditLogService;
        this.defaultGraceMinutes = defaultGraceMinutes;
        this.maxGraceMinutes = maxGraceMinutes;
    }

    @Transactional
    public WebhookSecretRotationResponse rotate(String webhookId,
                                                String newSigningSecret,
                                                Integer requestedGraceMinutes,
                                                String actor) {
        WebhookRegistrationEntity registration = registrationRepository
                .findByWebhookIdForUpdate(webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook registration not found"));

        int graceMinutes = requestedGraceMinutes == null
                ? defaultGraceMinutes
                : requestedGraceMinutes;
        if (graceMinutes < 1 || graceMinutes > maxGraceMinutes) {
            throw new IllegalArgumentException(
                    "graceMinutes must be between 1 and " + maxGraceMinutes);
        }

        String newHash = ApiKeyHashUtil.hash(newSigningSecret);
        if (newHash.equals(registration.getSecretHash())) {
            throw new IllegalArgumentException("New webhook signing secret must differ from the current secret");
        }

        EncryptedSecret encrypted = secretEncryptionService.encrypt(newSigningSecret);
        int previousVersion = registration.getSecretVersion();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(graceMinutes);

        registration.setPreviousSecretCiphertext(registration.getSecretCiphertext());
        registration.setPreviousSecretExpiresAt(expiresAt);
        registration.setSecretCiphertext(encrypted.ciphertext());
        registration.setSecretKeyId(encrypted.keyId());
        registration.setSecretVersion(previousVersion + 1);
        registration.setSecretHash(newHash);
        registrationRepository.save(registration);

        auditLogService.log(
                "WEBHOOK_SECRET_ROTATED",
                "WEBHOOK_REGISTRATION",
                webhookId,
                actor == null || actor.isBlank() ? "SYSTEM" : actor,
                Map.of(
                        "pspId", registration.getPspId(),
                        "previousVersion", previousVersion,
                        "newVersion", registration.getSecretVersion(),
                        "previousSecretExpiresAt", expiresAt,
                        "keyId", encrypted.keyId()));

        return new WebhookSecretRotationResponse(
                webhookId,
                registration.getSecretVersion(),
                expiresAt,
                registration.getStatus());
    }
}
