package com.example.switching.compliance.legalhold.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.compliance.legalhold.dto.LegalHoldRequest;
import com.example.switching.compliance.legalhold.dto.LegalHoldResponse;
import com.example.switching.compliance.legalhold.entity.LegalHoldEntity;
import com.example.switching.compliance.legalhold.entity.LegalHoldScopeType;
import com.example.switching.compliance.legalhold.entity.LegalHoldStatus;
import com.example.switching.compliance.legalhold.repository.LegalHoldRepository;

@Service
public class LegalHoldService {

    private final LegalHoldRepository repository;
    private final AuditLogService auditLogService;

    public LegalHoldService(LegalHoldRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LegalHoldResponse request(LegalHoldRequest request, String actor) {
        validateDates(request.effectiveFrom(), request.effectiveTo());
        LegalHoldEntity hold = new LegalHoldEntity();
        hold.setHoldRef("LH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        hold.setScopeType(request.scopeType());
        hold.setScopeKey(normalizeScope(request.scopeType(), request.scopeKey()));
        hold.setEffectiveFrom(request.effectiveFrom());
        hold.setEffectiveTo(request.effectiveTo());
        hold.setReason(request.reason().trim());
        hold.setCaseReference(request.caseReference().trim());
        hold.setStatus(LegalHoldStatus.PENDING);
        hold.setRequestedBy(requireActor(actor));
        hold.setRequestedAt(LocalDateTime.now());
        LegalHoldEntity saved = repository.save(hold);
        auditLogService.log("LEGAL_HOLD_REQUESTED", "LEGAL_HOLD", saved.getHoldRef(), actor,
                new AuditPayload(saved.getScopeType().name(), saved.getScopeKey(), saved.getCaseReference(), saved.getStatus().name()));
        return LegalHoldResponse.from(saved);
    }

    @Transactional
    public LegalHoldResponse approve(Long id, String actor) {
        LegalHoldEntity hold = locked(id);
        require(hold.getStatus() == LegalHoldStatus.PENDING, "hold is not pending");
        String approver = requireActor(actor);
        require(!approver.equals(hold.getRequestedBy()), "requester cannot approve own legal hold");
        hold.setStatus(LegalHoldStatus.ACTIVE);
        hold.setApprovedBy(approver);
        hold.setApprovedAt(LocalDateTime.now());
        auditLogService.log("LEGAL_HOLD_ACTIVATED", "LEGAL_HOLD", hold.getHoldRef(), approver,
                new AuditPayload(hold.getScopeType().name(), hold.getScopeKey(), hold.getCaseReference(), hold.getStatus().name()));
        return LegalHoldResponse.from(repository.save(hold));
    }

    @Transactional
    public LegalHoldResponse requestRelease(Long id, String actor) {
        LegalHoldEntity hold = locked(id);
        require(hold.getStatus() == LegalHoldStatus.ACTIVE, "only active holds can request release");
        hold.setStatus(LegalHoldStatus.RELEASE_REQUESTED);
        hold.setReleaseRequestedBy(requireActor(actor));
        hold.setReleaseRequestedAt(LocalDateTime.now());
        auditLogService.log("LEGAL_HOLD_RELEASE_REQUESTED", "LEGAL_HOLD", hold.getHoldRef(), actor,
                new AuditPayload(hold.getScopeType().name(), hold.getScopeKey(), hold.getCaseReference(), hold.getStatus().name()));
        return LegalHoldResponse.from(repository.save(hold));
    }

    @Transactional
    public LegalHoldResponse approveRelease(Long id, String actor) {
        LegalHoldEntity hold = locked(id);
        require(hold.getStatus() == LegalHoldStatus.RELEASE_REQUESTED, "release is not pending");
        String releaser = requireActor(actor);
        require(!releaser.equals(hold.getReleaseRequestedBy()), "release requester cannot approve own release");
        hold.setStatus(LegalHoldStatus.RELEASED);
        hold.setReleasedBy(releaser);
        hold.setReleasedAt(LocalDateTime.now());
        auditLogService.log("LEGAL_HOLD_RELEASED", "LEGAL_HOLD", hold.getHoldRef(), releaser,
                new AuditPayload(hold.getScopeType().name(), hold.getScopeKey(), hold.getCaseReference(), hold.getStatus().name()));
        return LegalHoldResponse.from(repository.save(hold));
    }

    @Transactional(readOnly = true)
    public List<LegalHoldResponse> list(LegalHoldStatus status) {
        return repository.findByStatusOrderByRequestedAtDesc(status).stream().map(LegalHoldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean blocksPartitionDrop(String tableName, LocalDate businessDate) {
        return blockingHoldRef(tableName, businessDate).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<String> blockingHoldRef(String tableName, LocalDate businessDate) {
        return repository.findBlockingTableHolds(tableName, businessDate, PageRequest.of(0, 1)).stream()
                .findFirst().map(LegalHoldEntity::getHoldRef);
    }

    private LegalHoldEntity locked(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("legal hold not found: " + id));
    }

    private static String normalizeScope(LegalHoldScopeType type, String key) {
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("scopeKey is required");
        String value = key.trim();
        if (type == LegalHoldScopeType.TABLE) {
            if (!value.equals("*") && !value.matches("[a-z][a-z0-9_]{0,159}")) {
                throw new IllegalArgumentException("table scope must be a safe lower-case identifier or *");
            }
            return value;
        }
        return value;
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
        }
    }

    private static String requireActor(String actor) {
        if (!StringUtils.hasText(actor)) throw new IllegalArgumentException("authenticated actor required");
        return actor.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record AuditPayload(String scopeType, String scopeKey, String caseReference, String status) {}
}
