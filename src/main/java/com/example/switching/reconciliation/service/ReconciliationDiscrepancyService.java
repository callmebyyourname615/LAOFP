package com.example.switching.reconciliation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.reconciliation.dto.ReconDiscrepancyReport;
import com.example.switching.reconciliation.dto.ReconItemResponse;
import com.example.switching.reconciliation.entity.ReconciliationFileEntity;
import com.example.switching.reconciliation.entity.ReconciliationItemEntity;
import com.example.switching.reconciliation.repository.ReconciliationItemRepository;

/**
 * Produces discrepancy reports for reconciliation files.
 *
 * <p>Discrepancies are items with {@code match_status} = UNMATCHED or DISPUTED.
 */
@Service
public class ReconciliationDiscrepancyService {

    private final ReconciliationFileService   fileService;
    private final ReconciliationItemRepository itemRepository;

    public ReconciliationDiscrepancyService(ReconciliationFileService fileService,
                                             ReconciliationItemRepository itemRepository) {
        this.fileService    = fileService;
        this.itemRepository = itemRepository;
    }

    /**
     * Build a discrepancy report for the given reconciliation file.
     *
     * @param fileRef the reconciliation file reference
     * @return summary counts + all non-MATCHED items
     */
    @Transactional(readOnly = true)
    public ReconDiscrepancyReport getReport(String fileRef) {
        ReconciliationFileEntity file = fileService.requireFile(fileRef);
        long fileId = file.getId();

        int matchedCount  = itemRepository.countByFileIdAndMatchStatus(fileId, "MATCHED");
        int unmatchedCount = itemRepository.countByFileIdAndMatchStatus(fileId, "UNMATCHED");
        int disputedCount  = itemRepository.countByFileIdAndMatchStatus(fileId, "DISPUTED");
        int totalRecords   = file.getTotalRecords() != null ? file.getTotalRecords()
                             : (matchedCount + unmatchedCount + disputedCount);

        // Collect UNMATCHED and DISPUTED items as discrepancies
        List<ReconciliationItemEntity> unmatched =
                itemRepository.findByFileIdAndMatchStatusOrderByLineNumberAsc(fileId, "UNMATCHED");
        List<ReconciliationItemEntity> disputed =
                itemRepository.findByFileIdAndMatchStatusOrderByLineNumberAsc(fileId, "DISPUTED");

        List<ReconItemResponse> discrepancies = new ArrayList<>(unmatched.size() + disputed.size());
        unmatched.forEach(i -> discrepancies.add(toResponse(i)));
        disputed.forEach(i  -> discrepancies.add(toResponse(i)));

        // Sort by line number for readability
        discrepancies.sort((a, b) -> Integer.compare(a.lineNumber(), b.lineNumber()));

        return new ReconDiscrepancyReport(
                fileRef, totalRecords, matchedCount, unmatchedCount, disputedCount, discrepancies);
    }

    /** All items for a file (matched + discrepancies). */
    @Transactional(readOnly = true)
    public List<ReconItemResponse> getAllItems(String fileRef) {
        ReconciliationFileEntity file = fileService.requireFile(fileRef);
        return itemRepository.findByFileIdOrderByLineNumberAsc(file.getId())
                .stream().map(this::toResponse).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ReconItemResponse toResponse(ReconciliationItemEntity i) {
        return new ReconItemResponse(
                i.getId(),
                i.getFileId(),
                i.getLineNumber(),
                i.getTransactionRef(),
                i.getExternalRef(),
                i.getAmount(),
                i.getCurrency(),
                i.getMatchStatus(),
                i.getMismatchReason(),
                i.getReconciliationDate(),
                i.getMatchedAt()
        );
    }
}
