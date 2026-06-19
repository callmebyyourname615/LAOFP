package com.example.switching.compliance.legalhold.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.switching.compliance.legalhold.entity.LegalHoldEntity;

public record LegalHoldResponse(
        Long id, String holdRef, String scopeType, String scopeKey,
        LocalDate effectiveFrom, LocalDate effectiveTo, String reason, String caseReference,
        String status, String requestedBy, LocalDateTime requestedAt,
        String approvedBy, String releaseRequestedBy, String releasedBy
) {
    public static LegalHoldResponse from(LegalHoldEntity h) {
        return new LegalHoldResponse(h.getId(), h.getHoldRef(), h.getScopeType().name(), h.getScopeKey(),
                h.getEffectiveFrom(), h.getEffectiveTo(), h.getReason(), h.getCaseReference(),
                h.getStatus().name(), h.getRequestedBy(), h.getRequestedAt(), h.getApprovedBy(),
                h.getReleaseRequestedBy(), h.getReleasedBy());
    }
}
