package com.example.switching.vpa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.vpa.dto.VpaLookupRequest;
import com.example.switching.vpa.dto.VpaLookupResponse;
import com.example.switching.vpa.entity.BeneficiaryTokenEntity;
import com.example.switching.vpa.entity.VpaRegistrationEntity;
import com.example.switching.vpa.exception.VpaNotFoundException;
import com.example.switching.vpa.repository.VpaRegistrationRepository;

/**
 * Resolves a VPA address to a beneficiary token.
 *
 * <p>The caller (PSP) supplies {@code vpaType} + {@code vpaValue}; this service
 * looks up the active VPA record, mints a one-time beneficiary token, and
 * returns the token + display metadata.  The token must be passed to
 * {@code POST /api/transfers} within its TTL window.
 */
@Service
public class VpaLookupService {

    private final VpaRegistrationRepository vpaRepository;
    private final BeneficiaryTokenService   tokenService;

    public VpaLookupService(VpaRegistrationRepository vpaRepository,
                             BeneficiaryTokenService tokenService) {
        this.vpaRepository = vpaRepository;
        this.tokenService  = tokenService;
    }

    /**
     * Resolve a VPA and issue a one-time beneficiary token.
     *
     * @throws VpaNotFoundException if no ACTIVE VPA matches the (type, value) pair
     */
    @Transactional
    public VpaLookupResponse resolve(VpaLookupRequest req) {
        VpaRegistrationEntity vpa = vpaRepository
                .findByVpaTypeAndVpaValueAndStatus(
                        req.getVpaType(), req.getVpaValue(), "ACTIVE")
                .orElseThrow(() -> new VpaNotFoundException(
                        "No active VPA found for type=" + req.getVpaType()
                        + " value=" + req.getVpaValue()));

        BeneficiaryTokenEntity token = tokenService.issue(vpa.getVpaId());

        return new VpaLookupResponse(
                token.getTokenId(),
                vpa.getDisplayName(),
                vpa.getPspId(),
                vpa.getAccountType(),
                token.getExpiresAt());
    }
}
