package com.example.switching.reconciliation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.reconciliation.dto.ReconItemRequest;
import com.example.switching.reconciliation.entity.ReconciliationFileEntity;
import com.example.switching.reconciliation.entity.ReconciliationItemEntity;
import com.example.switching.reconciliation.repository.ReconciliationItemRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.repository.TransferRepository;

/**
 * Imports reconciliation line items and matches them against internal transfers.
 *
 * <p><b>Matching logic</b>
 * <ol>
 *   <li>No {@code transactionRef} → UNMATCHED ("no transaction reference")</li>
 *   <li>Transfer not found in our DB → UNMATCHED ("transaction not found")</li>
 *   <li>Transfer found but amount differs by more than 0.01 → DISPUTED</li>
 *   <li>Otherwise → MATCHED</li>
 * </ol>
 *
 * <p>Items are written via {@link JdbcTemplate} because {@code reconciliation_items}
 * is a range-partitioned table; the partition key ({@code reconciliation_date}) must
 * be present in the INSERT.
 */
@Service
public class ReconciliationMatchingService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMatchingService.class);
    private static final String SOURCE = "RECONCILIATION";
    private static final String ENTITY = "RECONCILIATION_ITEM";

    private static final String INSERT_ITEM_SQL = """
            INSERT INTO reconciliation_items
                (file_id, line_number, transaction_ref, external_ref,
                 amount, currency, match_status, mismatch_reason, reconciliation_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Tolerance for amount comparison (2 decimal places). */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final ReconciliationFileService  fileService;
    private final ReconciliationItemRepository itemRepository;
    private final TransferRepository         transferRepository;
    private final JdbcTemplate               jdbcTemplate;
    private final AuditLogService            auditLogService;

    public ReconciliationMatchingService(ReconciliationFileService fileService,
                                          ReconciliationItemRepository itemRepository,
                                          TransferRepository transferRepository,
                                          JdbcTemplate jdbcTemplate,
                                          AuditLogService auditLogService) {
        this.fileService       = fileService;
        this.itemRepository    = itemRepository;
        this.transferRepository = transferRepository;
        this.jdbcTemplate      = jdbcTemplate;
        this.auditLogService   = auditLogService;
    }

    /**
     * Import line items for a file and immediately run matching against internal transfers.
     *
     * @param fileRef the target reconciliation file
     * @param items   line items from the external bank file
     * @return the resolved {@link ReconciliationItemEntity} list (read back from DB)
     */
    @Transactional
    public List<ReconciliationItemEntity> importAndMatch(String fileRef, List<ReconItemRequest> items) {
        ReconciliationFileEntity file = fileService.requireFile(fileRef);
        if ("COMPLETED".equals(file.getStatus()) || "FAILED".equals(file.getStatus())) {
            throw new IllegalStateException(
                    "Cannot re-import items for file " + fileRef
                    + " in status: " + file.getStatus());
        }

        // Mark as PROCESSING
        fileService.updateStatus(fileRef, "PROCESSING", 0, 0);
        fileService.setTotalRecords(fileRef, items.size());

        LocalDate reconDate = file.getReconciliationDate();
        long fileId = file.getId();
        int matched = 0, unmatched = 0;

        for (ReconItemRequest req : items) {
            String status;
            String reason = null;

            if (req.getTransactionRef() == null || req.getTransactionRef().isBlank()) {
                status   = "UNMATCHED";
                reason   = "No transaction reference provided";
            } else {
                Optional<TransferEntity> transferOpt =
                        transferRepository.findByTransferRef(req.getTransactionRef());

                if (transferOpt.isEmpty()) {
                    status = "UNMATCHED";
                    reason = "Transaction not found in switching system";
                } else {
                    TransferEntity transfer = transferOpt.get();
                    reason = checkAmountMismatch(req.getAmount(), transfer.getAmount());
                    if (reason != null) {
                        status = "DISPUTED";
                    } else {
                        status = "MATCHED";
                    }
                }
            }

            jdbcTemplate.update(INSERT_ITEM_SQL,
                    fileId, req.getLineNumber(), req.getTransactionRef(), req.getExternalRef(),
                    req.getAmount(), req.getCurrency(), status, reason, reconDate);

            if ("MATCHED".equals(status)) matched++;
            else unmatched++;
        }

        // Update file counters and mark COMPLETED
        fileService.updateStatus(fileRef, "COMPLETED", matched, unmatched);

        auditLogService.log("RECON_MATCHING_COMPLETED", ENTITY, fileRef, SOURCE,
                Map.of("fileRef", fileRef,
                        "total", items.size(),
                        "matched", matched,
                        "unmatched", unmatched));
        log.info("Reconciliation matching done: fileRef={} total={} matched={} unmatched={}",
                fileRef, items.size(), matched, unmatched);

        return itemRepository.findByFileIdOrderByLineNumberAsc(fileId);
    }

    /**
     * Re-run matching on already-imported items (e.g. after a transfer status correction).
     */
    @Transactional
    public List<ReconciliationItemEntity> rematch(String fileRef) {
        ReconciliationFileEntity file = fileService.requireFile(fileRef);
        List<ReconciliationItemEntity> items =
                itemRepository.findByFileIdOrderByLineNumberAsc(file.getId());

        int matched = 0, unmatched = 0;

        for (ReconciliationItemEntity item : items) {
            String status;
            String reason = null;

            if (item.getTransactionRef() == null || item.getTransactionRef().isBlank()) {
                status = "UNMATCHED";
                reason = "No transaction reference provided";
            } else {
                Optional<TransferEntity> transferOpt =
                        transferRepository.findByTransferRef(item.getTransactionRef());
                if (transferOpt.isEmpty()) {
                    status = "UNMATCHED";
                    reason = "Transaction not found in switching system";
                } else {
                    reason = checkAmountMismatch(item.getAmount(), transferOpt.get().getAmount());
                    status = reason != null ? "DISPUTED" : "MATCHED";
                }
            }

            itemRepository.updateMatchResult(item.getId(), status, reason);

            if ("MATCHED".equals(status)) matched++;
            else unmatched++;
        }

        fileService.updateStatus(fileRef, "COMPLETED", matched, unmatched);
        log.info("Reconciliation rematch done: fileRef={} matched={} unmatched={}", fileRef, matched, unmatched);
        return itemRepository.findByFileIdOrderByLineNumberAsc(file.getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Returns a mismatch description if amounts differ beyond tolerance, else null. */
    private String checkAmountMismatch(BigDecimal itemAmount, BigDecimal transferAmount) {
        if (itemAmount == null || transferAmount == null) {
            return null; // cannot compare — treat as matched
        }
        if (itemAmount.subtract(transferAmount).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            return "Amount mismatch: file=" + itemAmount + " system=" + transferAmount;
        }
        return null;
    }
}
