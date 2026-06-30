package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.repository.TransferRepository;

/**
 * Batches SETTLED transfers into settlement_items and upserts settlement_positions.
 *
 * <p>Both target tables are date-partitioned, so inserts go through {@link JdbcTemplate}
 * rather than JPA to avoid Hibernate's partition-unaware identity-column handling.
 *
 * <p>For each SETTLED transfer on the cycle's settlement date, two rows are written:
 * <ul>
 *   <li>DEBIT  — source bank (money leaving)</li>
 *   <li>CREDIT — destination bank (money arriving)</li>
 * </ul>
 * The corresponding {@code settlement_positions} row is upserted (accumulated) for each bank.
 */
@Service
public class SettlementBatchService {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchService.class);
    private static final String SOURCE = "SETTLEMENT";
    private static final String ENTITY = "SETTLEMENT_BATCH";

    /** Insert one item row (partition key = settlement_date must be supplied). */
    private static final String INSERT_ITEM_SQL = """
            INSERT INTO settlement_items
                (cycle_id, bank_code, transaction_ref, direction, amount, currency, settlement_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (cycle_id, transaction_ref, bank_code, direction, settlement_date)
            DO NOTHING
            """;

    /**
     * Upsert position row — accumulate debit/credit totals and transaction count.
     * net_position is a GENERATED ALWAYS AS (credit - debit) STORED column; never included.
     */
    private static final String UPSERT_POSITION_SQL = """
            INSERT INTO settlement_positions
                (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (cycle_id, bank_code, currency) DO UPDATE
            SET debit_amount      = settlement_positions.debit_amount      + EXCLUDED.debit_amount,
                credit_amount     = settlement_positions.credit_amount     + EXCLUDED.credit_amount,
                transaction_count = settlement_positions.transaction_count + EXCLUDED.transaction_count,
                updated_at        = NOW()
            """;

    private final SettlementCycleService settlementCycleService;
    private final TransferRepository transferRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final SettlementDateService settlementDateService;

    public SettlementBatchService(SettlementCycleService settlementCycleService,
                                   TransferRepository transferRepository,
                                   JdbcTemplate jdbcTemplate,
                                   AuditLogService auditLogService,
                                   SettlementDateService settlementDateService) {
        this.settlementCycleService = settlementCycleService;
        this.transferRepository = transferRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
        this.settlementDateService = settlementDateService;
    }

    /**
     * Collect all connector-confirmed transfers from the previous business day (T) for the
     * cycle's settlement date (T+1), then write
     * settlement_items + upsert settlement_positions.
     *
     * <p>The cycle must be OPEN or CLOSED; re-batching an already-SETTLED cycle is rejected.
     *
     * @param cycleRef identifies the target settlement cycle
     * @return number of transfers newly inserted or partially repaired; a clean retry returns zero
     */
    @Transactional
    public int batchTransactions(String cycleRef) {
        SettlementCycleEntity cycle = settlementCycleService.requireCycle(cycleRef);
        String status = cycle.getStatus();
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new IllegalStateException(
                    "Cannot batch transactions for cycle " + cycleRef
                    + " in status: " + status + " (expected OPEN or CLOSED)");
        }

        LocalDate settlementDate = cycle.getSettlementDate();
        LocalDate businessDate = settlementDateService.previousBusinessDay(settlementDate);
        List<TransferEntity> transfers =
                transferRepository.findByStatusAndBusinessDateAndSettlementMethod(
                        TransferStatus.READY_FOR_SETTLEMENT, businessDate, "DNS");

        if (transfers.isEmpty()) {
            log.info("No READY_FOR_SETTLEMENT transfers for businessDate={} settlementDate={} cycleRef={}",
                    businessDate, settlementDate, cycleRef);
            return 0;
        }

        long cycleId = cycle.getId();

        int batchCount = 0;
        int itemRowsInserted = 0;
        for (TransferEntity t : transfers) {
            String      txnRef = t.getTransferRef();
            if (isAlreadyBatchedForSettlementDate(txnRef, settlementDate)) {
                log.info("Skip already batched transferRef={} settlementDate={} targetCycleRef={}",
                        txnRef, settlementDate, cycleRef);
                continue;
            }

            BigDecimal  amt    = t.getAmount();
            String      ccy    = t.getCurrency() != null ? t.getCurrency() : "LAK";
            String      src    = t.getSourceBank();
            String      dst    = t.getDestinationBank();

            // Update a position only when its immutable settlement item was inserted.
            // This makes retries/re-batching idempotent and also repairs a partial batch
            // where only one side was committed before an earlier failure.
            int debitInserted = jdbcTemplate.update(INSERT_ITEM_SQL,
                    cycleId, src, txnRef, "DEBIT", amt, ccy, settlementDate);
            if (debitInserted == 1) {
                jdbcTemplate.update(UPSERT_POSITION_SQL,
                        cycleId, src, ccy, amt, BigDecimal.ZERO, 1);
            }

            int creditInserted = jdbcTemplate.update(INSERT_ITEM_SQL,
                    cycleId, dst, txnRef, "CREDIT", amt, ccy, settlementDate);
            if (creditInserted == 1) {
                jdbcTemplate.update(UPSERT_POSITION_SQL,
                        cycleId, dst, ccy, BigDecimal.ZERO, amt, 1);
            }

            itemRowsInserted += debitInserted + creditInserted;
            if (debitInserted == 1 || creditInserted == 1) {
                batchCount++;
            }
        }
        auditLogService.log("SETTLEMENT_BATCH_COMPLETED", ENTITY, cycleRef, SOURCE,
                Map.of("cycleRef", cycleRef,
                        "settlementDate", settlementDate.toString(),
                        "businessDate", businessDate.toString(),
                        "settlementModel", "T_PLUS_1",
                        "transferCount", batchCount,
                        "itemRowsInserted", itemRowsInserted));
        log.info("T+1 settlement batch done: cycleRef={} settlementDate={} businessDate={} transfers={} itemRows={}",
                cycleRef, settlementDate, businessDate, batchCount, itemRowsInserted);
        return batchCount;
    }

    private boolean isAlreadyBatchedForSettlementDate(String transactionRef, LocalDate settlementDate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM settlement_items
                WHERE transaction_ref = ?
                  AND settlement_date = ?
                """,
                Integer.class,
                transactionRef,
                settlementDate);
        return count != null && count > 0;
    }

    /** Count settlement_items rows already written for a cycle (cross-partition aggregate). */
    public int countItems(Long cycleId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_items WHERE cycle_id = ?",
                Integer.class, cycleId);
        return n != null ? n : 0;
    }
}
