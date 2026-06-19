package com.example.switching.settlement.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.maintenance.service.SchedulerLockService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;

@Profile("!migration")
@Component
public class SettlementCutoffScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementCutoffScheduler.class);
    private static final String ENTITY = "SETTLEMENT_CYCLE";
    private static final String SOURCE = "SETTLEMENT";

    private final SettlementCycleService cycleService;
    private final SettlementBatchService batchService;
    private final SettlementInstructionService instructionService;
    private final SchedulerLockService lockService;
    private final AuditLogService auditLogService;

    public SettlementCutoffScheduler(SettlementCycleService cycleService,
                                     SettlementBatchService batchService,
                                     SettlementInstructionService instructionService,
                                     SchedulerLockService lockService,
                                     AuditLogService auditLogService) {
        this.cycleService = cycleService;
        this.batchService = batchService;
        this.instructionService = instructionService;
        this.lockService = lockService;
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "${switching.settlement.cycle1-cron:0 45 8 * * MON-FRI}", zone = "${switching.settlement.time-zone:Asia/Vientiane}")
    public void runCycle1Cutoff() {
        runCutoffCycle(1);
    }

    @Scheduled(cron = "${switching.settlement.cycle2-cron:0 45 11 * * MON-FRI}", zone = "${switching.settlement.time-zone:Asia/Vientiane}")
    public void runCycle2Cutoff() {
        runCutoffCycle(2);
    }

    @Scheduled(cron = "${switching.settlement.cycle3-cron:0 15 15 * * MON-FRI}", zone = "${switching.settlement.time-zone:Asia/Vientiane}")
    public void runCycle3Cutoff() {
        runCutoffCycle(3);
    }

    @Scheduled(cron = "${switching.settlement.cycle4-cron:0 45 19 * * MON-FRI}", zone = "${switching.settlement.time-zone:Asia/Vientiane}")
    public void runCycle4Cutoff() {
        runCutoffCycle(4);
    }

    public CutoffRunResult runCutoffCycle(int cycleNumber) {
        String lockName = "SETTLEMENT_CUTOFF_C" + cycleNumber;
        if (!lockService.acquire(lockName, 20)) {
            log.debug("Settlement cutoff lock not acquired: cycleNumber={}", cycleNumber);
            return new CutoffRunResult(cycleNumber, null, false, 0, 0);
        }
        try {
            SettlementCycleEntity cycle = cycleService.openCycle(null);
            int transferCount = batchService.batchTransactions(cycle.getCycleRef());
            cycleService.closeCycle(cycle.getCycleRef());
            List<SettlementInstructionEntity> instructions =
                    instructionService.generateForCycle(cycle.getCycleRef());

            auditLogService.log("SETTLEMENT_CUTOFF_CYCLE_COMPLETED", ENTITY, cycle.getCycleRef(), SOURCE,
                    Map.of(
                            "cycleNumber", cycleNumber,
                            "cycleRef", cycle.getCycleRef(),
                            "transferCount", transferCount,
                            "instructionCount", instructions.size()));
            log.info("Settlement cutoff cycle completed: cycleNumber={} cycleRef={} transfers={} instructions={}",
                    cycleNumber, cycle.getCycleRef(), transferCount, instructions.size());
            return new CutoffRunResult(cycleNumber, cycle.getCycleRef(), true, transferCount, instructions.size());
        } catch (Exception ex) {
            log.error("Settlement cutoff cycle failed: cycleNumber={} error={}", cycleNumber, ex.getMessage(), ex);
            throw ex;
        } finally {
            lockService.release(lockName);
        }
    }

    public record CutoffRunResult(
            int cycleNumber,
            String cycleRef,
            boolean executed,
            int transferCount,
            int instructionCount) {}
}
