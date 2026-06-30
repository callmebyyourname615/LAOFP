package com.example.switching.settlement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.repository.SettlementCycleRepository;

/**
 * Manages the lifecycle of settlement cycles.
 *
 * <p>A cycle progresses: OPEN → CLOSED → SETTLED (or FAILED).
 * Multiple cycles per day are supported (up to 4 intraday cycles).
 */
@Service
public class SettlementCycleService {

    private static final Logger log = LoggerFactory.getLogger(SettlementCycleService.class);
    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_CYCLE";

    private final SettlementCycleRepository cycleRepository;
    private final AuditLogService auditLogService;
    private final SettlementDateService settlementDateService;

    public SettlementCycleService(SettlementCycleRepository cycleRepository,
                                  AuditLogService auditLogService,
                                  SettlementDateService settlementDateService) {
        this.cycleRepository = cycleRepository;
        this.auditLogService = auditLogService;
        this.settlementDateService = settlementDateService;
    }

    /** Open a new T+1 cycle for the given settlement date (auto-assigns cycle number 1-4). */
    @Transactional
    public SettlementCycleEntity openCycle(LocalDate settlementDate) {
        LocalDate effectiveSettlementDate = settlementDate != null
                ? settlementDate
                : settlementDateService.nextBusinessDay(LocalDate.now());

        int existing = cycleRepository.countBySettlementDate(effectiveSettlementDate);
        if (existing >= 4) {
            throw new IllegalStateException(
                    "Maximum 4 cycles per day reached for date: " + effectiveSettlementDate);
        }

        short cycleNumber = (short) (existing + 1);
        String cycleRef = generateCycleRef(effectiveSettlementDate, cycleNumber);
        LocalDateTime now = LocalDateTime.now();

        SettlementCycleEntity cycle = new SettlementCycleEntity();
        cycle.setCycleRef(cycleRef);
        cycle.setSettlementDate(effectiveSettlementDate);
        cycle.setCycleNumber(cycleNumber);
        cycle.setStatus("OPEN");
        cycle.setOpenedAt(now);

        cycle = cycleRepository.save(cycle);

        auditLogService.log("SETTLEMENT_CYCLE_OPENED", ENTITY, cycleRef, SOURCE,
                java.util.Map.of("cycleRef", cycleRef,
                        "settlementDate", effectiveSettlementDate,
                        "businessDate", settlementDateService.previousBusinessDay(effectiveSettlementDate),
                        "settlementModel", "T_PLUS_1",
                        "cycleNumber", cycleNumber));
        log.info("T+1 settlement cycle opened: cycleRef={} settlementDate={} businessDate={} number={}",
                cycleRef,
                effectiveSettlementDate,
                settlementDateService.previousBusinessDay(effectiveSettlementDate),
                cycleNumber);
        return cycle;
    }

    /** Close an OPEN cycle — no more transactions will be batched after this. */
    @Transactional
    public SettlementCycleEntity closeCycle(String cycleRef) {
        SettlementCycleEntity cycle = requireCycle(cycleRef);
        if ("CLOSED".equals(cycle.getStatus()) || "SETTLED".equals(cycle.getStatus())) {
            log.info("Settlement cycle close skipped: cycleRef={} status={}",
                    cycleRef, cycle.getStatus());
            return cycle;
        }
        requireStatus(cycle, "OPEN");

        cycle.setStatus("CLOSED");
        cycle.setClosedAt(LocalDateTime.now());
        cycle = cycleRepository.save(cycle);

        auditLogService.log("SETTLEMENT_CYCLE_CLOSED", ENTITY, cycleRef, SOURCE,
                java.util.Map.of("cycleRef", cycleRef));
        log.info("Settlement cycle closed: cycleRef={}", cycleRef);
        return cycle;
    }

    /**
     * Mark a CLOSED cycle as SETTLED after net positions have been applied.
     * Called by {@link SettlementNetPositionService#settle(String)}.
     */
    @Transactional
    public SettlementCycleEntity markSettled(String cycleRef) {
        SettlementCycleEntity cycle = requireCycle(cycleRef);
        requireStatus(cycle, "CLOSED");

        cycle.setStatus("SETTLED");
        cycle.setSettledAt(LocalDateTime.now());
        cycle = cycleRepository.save(cycle);

        auditLogService.log("SETTLEMENT_CYCLE_SETTLED", ENTITY, cycleRef, SOURCE,
                java.util.Map.of("cycleRef", cycleRef, "settledAt", cycle.getSettledAt()));
        log.info("Settlement cycle settled: cycleRef={}", cycleRef);
        return cycle;
    }

    /** List all cycles for a given date. */
    @Transactional(readOnly = true)
    public List<SettlementCycleEntity> listByDate(LocalDate date) {
        return cycleRepository.findBySettlementDateOrderByCycleNumberAsc(date);
    }

    /** List cycles by status (e.g. all OPEN). */
    @Transactional(readOnly = true)
    public List<SettlementCycleEntity> listByStatus(String status) {
        return cycleRepository.findByStatusOrderBySettlementDateDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public SettlementCycleEntity requireCycle(String cycleRef) {
        return cycleRepository.findByCycleRef(cycleRef)
                .orElseThrow(() -> new IllegalArgumentException("Settlement cycle not found: " + cycleRef));
    }

    @Transactional(readOnly = true)
    public SettlementCycleEntity requireCycleById(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement cycle not found: " + cycleId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireStatus(SettlementCycleEntity cycle, String expected) {
        if (!expected.equals(cycle.getStatus())) {
            throw new IllegalStateException(
                    "Cannot transition cycle " + cycle.getCycleRef()
                    + " from " + cycle.getStatus() + " — expected " + expected);
        }
    }

    private String generateCycleRef(LocalDate date, short cycleNumber) {
        return "SC-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-C" + cycleNumber;
    }
}
