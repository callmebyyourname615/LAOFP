package com.example.switching.participant.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.participant.dto.RegisterCertificateRequest;
import com.example.switching.participant.dto.RegisterCertificateResponse;
import com.example.switching.participant.dto.RotateCredentialsResponse;
import com.example.switching.participant.service.ParticipantCredentialService;

/**
 * P9 Step 4 — PSP credential management endpoints (ADMIN only).
 *
 * <ul>
 *   <li>{@code POST /v1/participants/{pspId}/credentials/rotate} — rotate client secret</li>
 *   <li>{@code POST /v1/participants/{pspId}/certificates/register} — register mTLS cert</li>
 *   <li>{@code DELETE /v1/participants/{pspId}/certificates/{certId}} — revoke cert</li>
 * </ul>
 *
 * <p>All endpoints require {@code ROLE_ADMIN}.  Role enforcement is configured in
 * {@code SecurityConfig}.
 */
@RestController
@RequestMapping("${switching.api.v1-prefix}/participants")
public class ParticipantCredentialController {

    private final ParticipantCredentialService credentialService;

    public ParticipantCredentialController(ParticipantCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * Rotates the OAuth client secret for the given PSP.
     * The new plain-text secret is returned exactly once in the response body.
     */
    @PostMapping("/{pspId}/credentials/rotate")
    public ResponseEntity<RotateCredentialsResponse> rotateCredentials(
            @PathVariable String pspId) {
        RotateCredentialsResponse response = credentialService.rotateCredentials(pspId);
        return ResponseEntity.ok(response);
    }

    /**
     * Registers an X.509 client certificate for the given PSP.
     * The SHA-256 fingerprint of the cert is stored in {@code psp_certificates}
     * with status {@code ACTIVE} so that subsequent mTLS requests are accepted.
     */
    @PostMapping("/{pspId}/certificates/register")
    public ResponseEntity<RegisterCertificateResponse> registerCertificate(
            @PathVariable String pspId,
            @RequestBody RegisterCertificateRequest request) {
        RegisterCertificateResponse response = credentialService.registerCertificate(pspId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Revokes the mTLS certificate identified by {@code certId}.
     * After revocation any mTLS handshake presenting that certificate returns
     * 401 LFP-2002.
     */
    @DeleteMapping("/{pspId}/certificates/{certId}")
    public ResponseEntity<Void> revokeCertificate(
            @PathVariable String pspId,
            @PathVariable String certId) {
        credentialService.revokeCertificate(certId);
        return ResponseEntity.noContent().build();
    }
}
