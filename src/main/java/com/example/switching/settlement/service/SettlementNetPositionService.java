package com.example.switching.settlement.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.repository.SettlementPositionRepository;

/**
 * Applies multilateral netting for a settlement cycle.
 *
 * <p>Netting is implicit: each participant's {@code net_position} is already a DB-generated
 * computed column ({@code credit_amount - debit_amount}).  This service marks every position
 * row SETTLED and advances the cycle to SETTLED status.
 *
 * <p>Lifecycle: cycle must be CLOSED before calling {@link #settle(String)}.
 * The caller (controller or scheduler) is responsible for closing the cycle first
 * via {@link SettlementCycleService#closeCycle(String)}.
 */
@Service
public class SettlementNetPositionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementNetPositionService.class);
    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_NET_POSITION";

    private final SettlementCycleService settlementCycleService;
    private final SettlementPositionRepository positionRepository;
    private final AuditLogService auditLogService;

    public SettlementNetPositionService(SettlementCycleService settlementCycleService,
                                         SettlementPositionRepository positionRepository,
                                         AuditLogService auditLogService) {
        this.settlementCycleService = settlementCycleService;
        this.positionRepository = positionRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Apply netting to a CLOSED cycle.
     *
     * <ol>
     *   <li>Bulk-update all {@code settlement_positions} rows to {@code status=SETTLED}.</li>
     *   <li>Advance the cycle to {@code SETTLED} via {@link SettlementCycleService#markSettled}.</li>
     * </ol>
     *
     * @param cycleRef the cycle to settle
     * @return the updated net-position rows (refreshed after bulk update)
     */
    @Transactional
    public List<SettlementPositionEntity> settle(String cycleRef) {
        SettlementCycleEntity cycle = settlementCycleService.requireCycle(cycleRef);
        if (!"CLOSED".equals(cycle.getStatus())) {
            throw new IllegalStateException(
                    "Cycle must be CLOSED before netting can run. Current status: "
                    + cycle.getStatus() + " (cycleRef=" + cycleRef + ")");
        }

        long cycleId = cycle.getId();

        List<SettlementPositionEntity> positions =
                positionRepository.findByCycleIdOrderByBankCodeAsc(cycleId);

        if (positions.isEmpty()) {
            log.warn("No positions found for cycleRef={}; cycle will be SETTLED with zero positions", cycleRef);
        }

        // Bulk mark all position rows as SETTLED
        int updated = positionRepository.markAllSettledByCycleId(cycleId);

        // Advance the cycle entity
        settlementCycleService.markSettled(cycleRef);

        auditLogService.log("SETTLEMENT_NET_POSITIONS_APPLIED", ENTITY, cycleRef, SOURCE,
                Map.of("cycleRef", cycleRef,
                        "positionCount", positions.size(),
                        "rowsMarkedSettled", updated));

        log.info("Multilateral netting applied: cycleRef={} participants={} positionRowsUpdated={}",
                cycleRef, positions.size(), updated);

        // Return fresh view (settledAt timestamps now populated by DB trigger / bulk update)
        return positionRepository.findByCycleIdOrderByBankCodeAsc(cycleId);
    }

    /**
     * Read-only view of net positions for any cycle — no state change.
     *
     * @param cycleRef identifies the cycle
     * @return positions ordered by bank code ascending
     */
    @Transactional(readOnly = true)
    public List<SettlementPositionEntity> getPositions(String cycleRef) {
        SettlementCycleEntity cycle = settlementCycleService.requireCycle(cycleRef);
        return positionRepository.findByCycleIdOrderByBankCodeAsc(cycle.getId());
    }
}
