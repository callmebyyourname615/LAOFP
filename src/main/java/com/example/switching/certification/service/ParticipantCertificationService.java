package com.example.switching.certification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.certification.dto.ParticipantCertificationRecordRequest;
import com.example.switching.certification.dto.ParticipantCertificationResponse;
import com.example.switching.certification.entity.ParticipantCertificationEntity;
import com.example.switching.certification.entity.ParticipantCertificationResult;
import com.example.switching.certification.repository.ParticipantCertificationRepository;
import com.example.switching.participant.repository.ParticipantRepository;
import com.example.switching.security.util.SensitiveDataSanitizer;

@Service
public class ParticipantCertificationService {

    private static final Pattern SHA = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");

    private final ParticipantCertificationRepository repository;
    private final ParticipantRepository participantRepository;
    private final AuditLogService auditLogService;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    public ParticipantCertificationService(ParticipantCertificationRepository repository,
                                           ParticipantRepository participantRepository,
                                           AuditLogService auditLogService,
                                           SensitiveDataSanitizer sensitiveDataSanitizer) {
        this.repository = repository;
        this.participantRepository = participantRepository;
        this.auditLogService = auditLogService;
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
    }

    @Transactional
    public ParticipantCertificationResponse record(ParticipantCertificationRecordRequest request, String actor) {
        String bankCode = normalizeBank(request.bankCode());
        participantRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new IllegalArgumentException("participant not found: " + bankCode));
        require(SHA.matcher(request.gitCommit()).matches(), "gitCommit must be full lowercase SHA");
        require(DIGEST.matcher(request.imageDigest()).matches(), "invalid image digest");
        require(HASH.matcher(request.evidenceSha256()).matches(), "invalid evidence SHA-256");
        LocalDateTime now = LocalDateTime.now();
        require(!request.executedAt().isAfter(now.plusMinutes(5)), "executedAt cannot be in the future");
        require(request.expiresAt().isAfter(request.executedAt()), "expiresAt must be after executedAt");
        require(request.expiresAt().isAfter(now), "expired certification evidence cannot be recorded as current");
        require(!request.expiresAt().isAfter(request.executedAt().plusDays(370)), "certification validity cannot exceed 370 days");

        ParticipantCertificationEntity entity = new ParticipantCertificationEntity();
        entity.setCertificationRef("CERT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        entity.setBankCode(bankCode);
        entity.setSuiteVersion(request.suiteVersion().trim());
        entity.setGitCommit(request.gitCommit());
        entity.setImageDigest(request.imageDigest());
        entity.setEvidenceSha256(request.evidenceSha256());
        entity.setResult(request.result());
        entity.setExecutedAt(request.executedAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setRecordedBy(requireActor(actor));
        entity.setRecordedAt(LocalDateTime.now());
        entity.setDetailsJson(sensitiveDataSanitizer.sanitizeJson(request.detailsJson()));
        ParticipantCertificationEntity saved = repository.save(entity);
        auditLogService.log("PARTICIPANT_CERTIFICATION_RECORDED", "PARTICIPANT_CERTIFICATION",
                saved.getCertificationRef(), actor,
                new AuditPayload(saved.getBankCode(), saved.getSuiteVersion(), saved.getGitCommit(),
                        saved.getImageDigest(), saved.getEvidenceSha256(), saved.getResult().name(), saved.getExpiresAt()));
        return ParticipantCertificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentPass(String bankCode) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findFirstByBankCodeOrderByExecutedAtDescIdDesc(normalizeBank(bankCode))
                .filter(certification -> certification.getResult() == ParticipantCertificationResult.PASS)
                .filter(certification -> certification.getExpiresAt().isAfter(now))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<ParticipantCertificationResponse> list(String bankCode) {
        return repository.findTop100ByBankCodeOrderByExecutedAtDesc(normalizeBank(bankCode)).stream()
                .map(ParticipantCertificationResponse::from).toList();
    }

    private static String normalizeBank(String value) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("bankCode is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireActor(String value) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("authenticated actor required");
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record AuditPayload(String bankCode, String suiteVersion, String gitCommit, String imageDigest,
                                String evidenceSha256, String result, LocalDateTime expiresAt) {}
}
