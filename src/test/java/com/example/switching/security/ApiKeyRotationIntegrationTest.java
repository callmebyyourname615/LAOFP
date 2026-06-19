package com.example.switching.security;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.security.dto.ApiKeyCreateRequest;
import com.example.switching.security.dto.ApiKeyResponse;
import com.example.switching.security.repository.ApiKeyRepository;
import com.example.switching.security.service.ApiKeyService;
import com.example.switching.security.util.ApiKeyHashUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P4 — Key rotation: verifies that after rotate() the old key is immediately invalid.
 *
 * The API key auth filter authenticates by calling
 * {@code ApiKeyRepository.findByKeyValueAndEnabledTrue(sha256(plainKey))}.
 * After rotation the stored hash changes, so the lookup for the old hash
 * returns empty — exactly what causes a 401 on the very next request.
 *
 * TC-KR-001  Freshly created key hash is found in repository
 * TC-KR-002  After rotation, old key hash is NOT found (auth will fail)
 * TC-KR-003  After rotation, new key hash IS found (new key works immediately)
 * TC-KR-004  Disabled key hash is NOT found even if hash is correct
 */
class ApiKeyRotationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ApiKeyService      apiKeyService;
    @Autowired private ApiKeyRepository   apiKeyRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // TC-KR-001  Created key hash is found
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void createdKey_hashFoundInRepository() {
        ApiKeyResponse created = apiKeyService.create(bankKeyRequest("KR-001"));
        String oldHash = ApiKeyHashUtil.hash(created.getPlainKey());

        assertTrue(apiKeyRepository.findByKeyValueAndEnabledTrue(oldHash).isPresent(),
                "Newly created key hash must be found by the auth repository lookup");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-KR-002  After rotation, old hash is NOT found
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void afterRotation_oldKeyHashNotFound() {
        ApiKeyResponse created = apiKeyService.create(bankKeyRequest("KR-002"));
        String oldHash = ApiKeyHashUtil.hash(created.getPlainKey());

        apiKeyService.rotate(created.getId());

        assertFalse(apiKeyRepository.findByKeyValueAndEnabledTrue(oldHash).isPresent(),
                "Old key hash must NOT be found after rotation — any request using the old key gets 401");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-KR-003  After rotation, new key hash IS found immediately
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void afterRotation_newKeyHashFoundImmediately() {
        ApiKeyResponse created  = apiKeyService.create(bankKeyRequest("KR-003"));
        ApiKeyResponse rotated  = apiKeyService.rotate(created.getId());

        String newHash = ApiKeyHashUtil.hash(rotated.getPlainKey());

        assertTrue(apiKeyRepository.findByKeyValueAndEnabledTrue(newHash).isPresent(),
                "New key hash must be found immediately after rotation — zero downtime");
        assertNotEquals(ApiKeyHashUtil.hash(created.getPlainKey()), newHash,
                "New hash must be different from the old hash");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-KR-004  Disabled key hash is NOT found (disable() also blocks auth)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void disabledKey_hashNotFound() {
        ApiKeyResponse created = apiKeyService.create(bankKeyRequest("KR-004"));
        String hash = ApiKeyHashUtil.hash(created.getPlainKey());

        assertTrue(apiKeyRepository.findByKeyValueAndEnabledTrue(hash).isPresent(),
                "Key must be found before disable");

        apiKeyService.disable(created.getId());

        assertFalse(apiKeyRepository.findByKeyValueAndEnabledTrue(hash).isPresent(),
                "Disabled key must NOT be found — auth filter must reject it");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────
    private ApiKeyCreateRequest bankKeyRequest(String suffix) {
        ApiKeyCreateRequest req = new ApiKeyCreateRequest();
        req.setName("test-key-" + suffix);
        req.setRole("BANK");
        req.setBankCode("BANK_KR_TEST");
        req.setExpiresAt(null);
        return req;
    }
}
