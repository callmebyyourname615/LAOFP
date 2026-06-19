package com.example.switching.vpa.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.vpa.dto.VpaDetailResponse;
import com.example.switching.vpa.dto.VpaRegisterRequest;
import com.example.switching.vpa.dto.VpaUpdateRequest;
import com.example.switching.vpa.entity.VpaRegistrationEntity;
import com.example.switching.vpa.exception.VpaDuplicateException;
import com.example.switching.vpa.exception.VpaNotFoundException;
import com.example.switching.vpa.repository.VpaRegistrationRepository;

/**
 * CRUD operations for VPA registrations.
 *
 * <ul>
 *   <li>register — create a new ACTIVE VPA; LFP-3002 on (type, value) collision.</li>
 *   <li>update   — change accountRef / displayName on an ACTIVE VPA.</li>
 *   <li>deregister — soft-delete by setting status = 'INACTIVE'.</li>
 *   <li>getById  — fetch by public vpaId string.</li>
 * </ul>
 */
@Service
public class VpaRegistrationService {

    private final VpaRegistrationRepository vpaRepository;

    public VpaRegistrationService(VpaRegistrationRepository vpaRepository) {
        this.vpaRepository = vpaRepository;
    }

    // ─── register ────────────────────────────────────────────────────────────

    @Transactional
    public VpaDetailResponse register(VpaRegisterRequest req) {
        // Enforce partial unique index: only one ACTIVE row per (type, value)
        if (vpaRepository.existsByVpaTypeAndVpaValueAndStatus(
                req.getVpaType(), req.getVpaValue(), "ACTIVE")) {
            throw new VpaDuplicateException(
                    "An active VPA already exists for type=" + req.getVpaType()
                    + " value=" + req.getVpaValue());
        }

        VpaRegistrationEntity entity = new VpaRegistrationEntity();
        entity.setVpaId(UUID.randomUUID().toString());
        entity.setVpaType(req.getVpaType());
        entity.setVpaValue(req.getVpaValue());
        entity.setPspId(req.getPspId());
        entity.setAccountRef(req.getAccountRef());
        entity.setAccountType(req.getAccountType() != null ? req.getAccountType() : "BANK_ACCOUNT");
        entity.setDisplayName(req.getDisplayName());
        entity.setStatus("ACTIVE");

        vpaRepository.save(entity);
        return toDetail(entity);
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Transactional
    public VpaDetailResponse update(String vpaId, VpaUpdateRequest req) {
        VpaRegistrationEntity entity = requireActive(vpaId);
        entity.setAccountRef(req.getAccountRef());
        if (req.getDisplayName() != null) {
            entity.setDisplayName(req.getDisplayName());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        vpaRepository.save(entity);
        return toDetail(entity);
    }

    // ─── deregister ───────────────────────────────────────────────────────────

    @Transactional
    public void deregister(String vpaId) {
        VpaRegistrationEntity entity = requireActive(vpaId);
        entity.setStatus("INACTIVE");
        entity.setUpdatedAt(LocalDateTime.now());
        vpaRepository.save(entity);
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VpaDetailResponse getById(String vpaId) {
        VpaRegistrationEntity entity = vpaRepository.findByVpaId(vpaId)
                .orElseThrow(() -> new VpaNotFoundException(
                        "VPA not found: " + vpaId));
        return toDetail(entity);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private VpaRegistrationEntity requireActive(String vpaId) {
        VpaRegistrationEntity entity = vpaRepository.findByVpaId(vpaId)
                .orElseThrow(() -> new VpaNotFoundException(
                        "VPA not found: " + vpaId));
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new VpaNotFoundException(
                    "VPA is not active: " + vpaId);
        }
        return entity;
    }

    private VpaDetailResponse toDetail(VpaRegistrationEntity e) {
        return new VpaDetailResponse(
                e.getVpaId(), e.getVpaType(), e.getVpaValue(),
                e.getPspId(), e.getAccountRef(), e.getAccountType(),
                e.getDisplayName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
