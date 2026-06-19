package com.example.switching.outbox.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.config.FpreProperties;
import com.example.switching.outbox.entity.PspSuspensionLogEntity;
import com.example.switching.outbox.repository.PspSuspensionLogRepository;
import com.example.switching.outbox.repository.ReversalLogRepository;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.repository.ParticipantRepository;

/**
 * FPRE PSP auto-suspension.
 *
 * <p>After each auto-reversal this service counts how many reversals the destination
 * PSP has accumulated within the rolling {@code suspensionWindowMinutes} window.
 * If the count reaches {@code suspensionReversalThreshold} the PSP's inbound
 * transfer channel is suspended:
 * <ul>
 *   <li>participant status → {@code INBOUND_SUSPENDED}</li>
 *   <li>a {@code psp_suspension_log} row is inserted</li>
 *   <li>A {@code PARTICIPANT.STATUS_CHANGED} webhook is deferred to Phase 12</li>
 * </ul>
 */
@Service
public class PspAutoSuspensionService {

    private static final Logger log = LoggerFactory.getLogger(PspAutoSuspensionService.class);
    private static final String SOURCE_SYSTEM = "FPRE_SUSPENSION";
    private static final String ENTITY_TYPE   = "PARTICIPANT";

    private final FpreProperties          fpre;
    private final ReversalLogRepository   reversalLogRepository;
    private final PspSuspensionLogRepository suspensionLogRepository;
    private final ParticipantRepository   participantRepository;
    private final AuditLogService         auditLogService;

    public PspAutoSuspensionService(FpreProperties fpre,
                                    ReversalLogRepository reversalLogRepository,
                                    PspSuspensionLogRepository suspensionLogRepository,
                                    ParticipantRepository participantRepository,
                                    AuditLogService auditLogService) {
        this.fpre                  = fpre;
        this.reversalLogRepository = reversalLogRepository;
        this.suspensionLogRepository = suspensionLogRepository;
        this.participantRepository = participantRepository;
        this.auditLogService       = auditLogService;
    }

    /**
     * Check reversal count for {@code pspId} within the configured window.
     * If threshold is reached suspend the PSP.
     *
     * @param pspId bank_code of the destination PSP
     * @return {@code true} if the PSP was suspended this call
     */
    public boolean checkAndSuspend(String pspId) {
        LocalDateTime windowStart = LocalDateTime.now()
                .minusMinutes(fpre.getSuspensionWindowMinutes());

        long count = reversalLogRepository
                .countByDestinationBankAndTriggeredAtAfter(pspId, windowStart);

        log.debug("FPRE suspension check: pspId={} reversalsIn{}min={} threshold={}",
                pspId, fpre.getSuspensionWindowMinutes(), count,
                fpre.getSuspensionReversalThreshold());

        if (count < fpre.getSuspensionReversalThreshold()) {
            return false;
        }

        ParticipantEntity participant = participantRepository.findByBankCode(pspId).orElse(null);
        if (participant == null) {
            log.warn("PSP suspension skipped — participant not found: pspId={}", pspId);
            return false;
        }

        if (participant.getStatus() == ParticipantStatus.INBOUND_SUSPENDED) {
            log.debug("PSP already suspended: pspId={}", pspId);
            return false;
        }

        // ── Suspend ──────────────────────────────────────────────────────────
        participant.setStatus(ParticipantStatus.INBOUND_SUSPENDED);
        participantRepository.save(participant);

        LocalDateTime now = LocalDateTime.now();
        PspSuspensionLogEntity suspensionLog = new PspSuspensionLogEntity();
        suspensionLog.setPspId(pspId);
        suspensionLog.setSuspendedAt(now);
        suspensionLog.setReversalCount((int) count);
        suspensionLog.setWindowMinutes(fpre.getSuspensionWindowMinutes());
        suspensionLog.setCreatedAt(now);
        suspensionLogRepository.save(suspensionLog);

        auditLogService.log(
                "FPRE_PSP_AUTO_SUSPENDED",
                ENTITY_TYPE,
                pspId,
                SOURCE_SYSTEM,
                java.util.Map.of(
                        "pspId",          pspId,
                        "reversalCount",  count,
                        "windowMinutes",  fpre.getSuspensionWindowMinutes(),
                        "threshold",      fpre.getSuspensionReversalThreshold()));

        log.warn("FPRE PSP auto-suspended: pspId={} reversalCount={} windowMinutes={}",
                pspId, count, fpre.getSuspensionWindowMinutes());

        return true;
    }
}
