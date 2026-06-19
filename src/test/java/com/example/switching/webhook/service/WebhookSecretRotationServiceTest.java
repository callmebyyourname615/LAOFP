package com.example.switching.webhook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.security.util.ApiKeyHashUtil;
import com.example.switching.webhook.crypto.EncryptedSecret;
import com.example.switching.webhook.crypto.SecretEncryptionService;
import com.example.switching.webhook.entity.WebhookRegistrationEntity;
import com.example.switching.webhook.repository.WebhookRegistrationRepository;

@ExtendWith(MockitoExtension.class)
class WebhookSecretRotationServiceTest {

    @Mock WebhookRegistrationRepository repository;
    @Mock SecretEncryptionService encryptionService;
    @Mock AuditLogService auditLogService;

    private WebhookSecretRotationService service;
    private WebhookRegistrationEntity registration;

    @BeforeEach
    void setUp() {
        service = new WebhookSecretRotationService(
                repository,
                encryptionService,
                auditLogService,
                60,
                10080);

        registration = new WebhookRegistrationEntity();
        registration.setWebhookId("hook-1");
        registration.setPspId("BANK_A");
        registration.setStatus("ACTIVE");
        registration.setSecretCiphertext("env:v1:old");
        registration.setSecretKeyId("vault-transit:transit/switching-webhook");
        registration.setSecretVersion(2);
        registration.setSecretHash(ApiKeyHashUtil.hash(
                "old-secret-value-with-at-least-32-characters"));

        when(repository.findByWebhookIdForUpdate("hook-1"))
                .thenReturn(Optional.of(registration));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rotateMovesCurrentCiphertextIntoGraceSlotAndWritesAuditLog() {
        String newSecret = "new-secret-value-with-at-least-32-characters";
        when(encryptionService.encrypt(newSecret)).thenReturn(new EncryptedSecret(
                "env:v1:new",
                "vault-transit:transit/switching-webhook",
                1));

        var response = service.rotate("hook-1", newSecret, 30, "operator@example");

        assertEquals("env:v1:old", registration.getPreviousSecretCiphertext());
        assertNotNull(registration.getPreviousSecretExpiresAt());
        assertEquals("env:v1:new", registration.getSecretCiphertext());
        assertEquals(3, registration.getSecretVersion());
        assertEquals(3, response.secretVersion());
        verify(auditLogService).log(
                eq("WEBHOOK_SECRET_ROTATED"),
                eq("WEBHOOK_REGISTRATION"),
                eq("hook-1"),
                eq("operator@example"),
                any());
    }

    @Test
    void rotateRejectsReuseOfCurrentSecret() {
        String oldSecret = "old-secret-value-with-at-least-32-characters";
        assertThrows(
                IllegalArgumentException.class,
                () -> service.rotate("hook-1", oldSecret, 60, "operator"));
    }

    @Test
    void rotateRejectsGraceOutsidePolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.rotate(
                        "hook-1",
                        "another-new-secret-with-at-least-32-characters",
                        10081,
                        "operator"));
    }
}
