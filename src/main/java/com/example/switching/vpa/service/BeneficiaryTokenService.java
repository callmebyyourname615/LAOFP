package com.example.switching.vpa.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.vpa.entity.BeneficiaryTokenEntity;
import com.example.switching.vpa.exception.BeneficiaryTokenExpiredException;
import com.example.switching.vpa.exception.BeneficiaryTokenUsedException;
import com.example.switching.vpa.repository.BeneficiaryTokenRepository;

/**
 * Manages one-time beneficiary tokens:
 * <ol>
 *   <li>{@link #issue(String)} — mint a fresh UUID token for a VPA resolve response.</li>
 *   <li>{@link #validate(String)} — check expiry + used flag without consuming.</li>
 *   <li>{@link #consume(String)} — validate then mark used; throws on failure.</li>
 * </ol>
 */
@Service
public class BeneficiaryTokenService {

    private final BeneficiaryTokenRepository tokenRepository;
    private final long tokenTtlSeconds;

    public BeneficiaryTokenService(
            BeneficiaryTokenRepository tokenRepository,
            @Value("${switching.vpa.token-ttl-seconds:300}") long tokenTtlSeconds) {
        this.tokenRepository  = tokenRepository;
        this.tokenTtlSeconds  = tokenTtlSeconds;
    }

    // ─── issue ───────────────────────────────────────────────────────────────

    /**
     * Mint and persist a new token linked to the given vpaId.
     *
     * @return the persisted {@link BeneficiaryTokenEntity}
     */
    @Transactional
    public BeneficiaryTokenEntity issue(String vpaId) {
        LocalDateTime now = LocalDateTime.now();
        BeneficiaryTokenEntity token = new BeneficiaryTokenEntity();
        token.setTokenId(UUID.randomUUID().toString());
        token.setVpaId(vpaId);
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusSeconds(tokenTtlSeconds));
        token.setUsed(false);
        return tokenRepository.save(token);
    }

    // ─── validate (read-only) ─────────────────────────────────────────────────

    /**
     * Resolve a token without consuming it.
     * Throws {@link BeneficiaryTokenExpiredException} if not found, expired, or already used.
     *
     * @return the valid, unconsumed {@link BeneficiaryTokenEntity}
     */
    @Transactional(readOnly = true)
    public BeneficiaryTokenEntity validate(String tokenId) {
        BeneficiaryTokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new BeneficiaryTokenExpiredException(
                        "Beneficiary token not found: " + tokenId));

        if (token.isUsed()) {
            throw new BeneficiaryTokenUsedException(
                    "Beneficiary token has already been used: " + tokenId);
        }
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new BeneficiaryTokenExpiredException(
                    "Beneficiary token has expired: " + tokenId);
        }
        return token;
    }

    // ─── consume ─────────────────────────────────────────────────────────────

    /**
     * Validate then atomically mark the token as used.
     * Idempotent guard: if the token is already used this throws
     * {@link BeneficiaryTokenUsedException} before any update.
     *
     * @return the vpaId the token was issued for
     */
    @Transactional
    public String consume(String tokenId) {
        BeneficiaryTokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new BeneficiaryTokenExpiredException(
                        "Beneficiary token not found: " + tokenId));

        if (token.isUsed()) {
            throw new BeneficiaryTokenUsedException(
                    "Beneficiary token has already been used: " + tokenId);
        }
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new BeneficiaryTokenExpiredException(
                    "Beneficiary token has expired: " + tokenId);
        }

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        return token.getVpaId();
    }
}
