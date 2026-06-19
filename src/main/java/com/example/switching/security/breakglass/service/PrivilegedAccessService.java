package com.example.switching.security.breakglass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.security.breakglass.dto.PrivilegedAccessRequest;
import com.example.switching.security.breakglass.dto.PrivilegedAccessResponse;
import com.example.switching.security.breakglass.entity.PrivilegedAccessSessionEntity;
import com.example.switching.security.breakglass.entity.PrivilegedAccessStatus;
import com.example.switching.security.breakglass.repository.PrivilegedAccessSessionRepository;

@Service
@Profile("!migration")
public class PrivilegedAccessService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final PrivilegedAccessSessionRepository repository;
    private final AuditLogService auditLogService;

    public PrivilegedAccessService(PrivilegedAccessSessionRepository repository,
                                   AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PrivilegedAccessResponse request(PrivilegedAccessRequest request, String actor) {
        PrivilegedAccessSessionEntity session = new PrivilegedAccessSessionEntity();
        session.setSessionRef("BG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        session.setRequestedBy(requireActor(actor));
        session.setRequestedAt(LocalDateTime.now());
        session.setReason(request.reason().trim());
        session.setTicketReference(request.ticketReference().trim());
        session.setRequestedTtlMinutes(request.ttlMinutes());
        session.setMaxUses(request.maxUses());
        session.setUseCount(0);
        session.setStatus(PrivilegedAccessStatus.PENDING);
        PrivilegedAccessSessionEntity saved = repository.save(session);
        auditLogService.log("BREAK_GLASS_REQUESTED", "PRIVILEGED_ACCESS", saved.getSessionRef(), actor,
                new AuditPayload(saved.getTicketReference(), saved.getRequestedTtlMinutes(), saved.getMaxUses(), saved.getStatus().name()));
        return PrivilegedAccessResponse.from(saved);
    }

    @Transactional
    public PrivilegedAccessResponse approve(Long id, String actor) {
        PrivilegedAccessSessionEntity session = locked(id);
        require(session.getStatus() == PrivilegedAccessStatus.PENDING, "session is not pending");
        require(!LocalDateTime.now().isAfter(session.getRequestedAt().plusHours(24)),
                "break-glass request approval window expired");
        String approver = requireActor(actor);
        require(!approver.equals(session.getRequestedBy()), "requester cannot approve own break-glass session");
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = "bg_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setTokenHash(sha256(rawToken));
        session.setTokenPrefix(rawToken.substring(0, Math.min(12, rawToken.length())));
        session.setApprovedBy(approver);
        session.setApprovedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(session.getRequestedTtlMinutes()));
        session.setStatus(PrivilegedAccessStatus.ACTIVE);
        PrivilegedAccessSessionEntity saved = repository.save(session);
        auditLogService.log("BREAK_GLASS_APPROVED", "PRIVILEGED_ACCESS", saved.getSessionRef(), approver,
                new AuditPayload(saved.getTicketReference(), saved.getRequestedTtlMinutes(), saved.getMaxUses(), saved.getStatus().name()));
        return PrivilegedAccessResponse.from(saved, rawToken);
    }

    @Transactional
    public void validateAndRecordUse(String rawToken, String actor, String action) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalStateException("break-glass token is required");
        }
        PrivilegedAccessSessionEntity session = repository.findByTokenHashForUpdate(sha256(rawToken.trim()))
                .orElseThrow(() -> new IllegalStateException("invalid break-glass token"));
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == PrivilegedAccessStatus.ACTIVE && session.getExpiresAt() != null && now.isAfter(session.getExpiresAt())) {
            session.setStatus(PrivilegedAccessStatus.EXPIRED);
            repository.save(session);
        }
        require(session.getStatus() == PrivilegedAccessStatus.ACTIVE, "break-glass session is not active");
        require(session.getExpiresAt() != null && !now.isAfter(session.getExpiresAt()), "break-glass session expired");
        require(session.getUseCount() < session.getMaxUses(), "break-glass session usage limit exhausted");
        require(session.getRequestedBy().equals(requireActor(actor)), "break-glass token belongs to a different actor");
        session.setUseCount(session.getUseCount() + 1);
        session.setLastUsedAt(now);
        repository.save(session);
        auditLogService.log("BREAK_GLASS_USED", "PRIVILEGED_ACCESS", session.getSessionRef(), actor,
                new UseAuditPayload(action, session.getUseCount(), session.getMaxUses(), session.getTokenPrefix()));
    }

    @Transactional
    public PrivilegedAccessResponse revoke(Long id, String actor) {
        PrivilegedAccessSessionEntity session = locked(id);
        require(session.getStatus() == PrivilegedAccessStatus.ACTIVE || session.getStatus() == PrivilegedAccessStatus.PENDING,
                "session cannot be revoked from status " + session.getStatus());
        session.setStatus(PrivilegedAccessStatus.REVOKED);
        session.setRevokedBy(requireActor(actor));
        session.setRevokedAt(LocalDateTime.now());
        session.setTokenHash(null);
        auditLogService.log("BREAK_GLASS_REVOKED", "PRIVILEGED_ACCESS", session.getSessionRef(), actor,
                new AuditPayload(session.getTicketReference(), session.getRequestedTtlMinutes(), session.getMaxUses(), session.getStatus().name()));
        return PrivilegedAccessResponse.from(repository.save(session));
    }

    @Transactional(readOnly = true)
    public List<PrivilegedAccessResponse> list(PrivilegedAccessStatus status) {
        return repository.findTop100ByStatusOrderByRequestedAtDesc(status).stream()
                .map(PrivilegedAccessResponse::from).toList();
    }

    @Scheduled(fixedDelayString = "${switching.security.break-glass.expiry-scan:PT1M}")
    @Transactional
    public void expireSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<PrivilegedAccessSessionEntity> expired = repository
                .findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        PrivilegedAccessStatus.ACTIVE, now, PageRequest.of(0, 200));
        List<PrivilegedAccessSessionEntity> stalePending = repository
                .findByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(
                        PrivilegedAccessStatus.PENDING, now.minusHours(24), PageRequest.of(0, 200));
        for (PrivilegedAccessSessionEntity session : expired) {
            expire(session, "BREAK_GLASS_EXPIRED");
        }
        for (PrivilegedAccessSessionEntity session : stalePending) {
            expire(session, "BREAK_GLASS_REQUEST_EXPIRED");
        }
        repository.saveAll(expired);
        repository.saveAll(stalePending);
    }

    private void expire(PrivilegedAccessSessionEntity session, String event) {
        session.setStatus(PrivilegedAccessStatus.EXPIRED);
        session.setTokenHash(null);
        auditLogService.log(event, "PRIVILEGED_ACCESS", session.getSessionRef(), "SYSTEM",
                new AuditPayload(session.getTicketReference(), session.getRequestedTtlMinutes(),
                        session.getMaxUses(), session.getStatus().name()));
    }

    private PrivilegedAccessSessionEntity locked(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("break-glass session not found: " + id));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String requireActor(String actor) {
        if (!StringUtils.hasText(actor)) throw new IllegalArgumentException("authenticated actor required");
        return actor.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record AuditPayload(String ticketReference, int ttlMinutes, int maxUses, String status) {}
    private record UseAuditPayload(String action, int useCount, int maxUses, String tokenPrefix) {}
}
