package com.example.switching.reconciliation.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.reconciliation.dto.CreateReconFileRequest;
import com.example.switching.reconciliation.entity.ReconciliationFileEntity;
import com.example.switching.reconciliation.repository.ReconciliationFileRepository;

/**
 * Manages the lifecycle of reconciliation files (metadata only — actual file bytes
 * are stored in MinIO and referenced via {@code object_key}).
 *
 * <p>Lifecycle: RECEIVED → PROCESSING → COMPLETED | FAILED
 */
@Service
public class ReconciliationFileService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationFileService.class);
    private static final String SOURCE = "RECONCILIATION";
    private static final String ENTITY = "RECONCILIATION_FILE";

    private final ReconciliationFileRepository fileRepository;
    private final AuditLogService auditLogService;

    public ReconciliationFileService(ReconciliationFileRepository fileRepository,
                                      AuditLogService auditLogService) {
        this.fileRepository = fileRepository;
        this.auditLogService = auditLogService;
    }

    /** Register a new reconciliation file. Returns the saved entity. */
    @Transactional
    public ReconciliationFileEntity createFile(CreateReconFileRequest request) {
        String fileRef = generateFileRef(request.getReconciliationDate());

        ReconciliationFileEntity file = new ReconciliationFileEntity();
        file.setFileRef(fileRef);
        file.setSourceBank(request.getSourceBank());
        file.setFileName(request.getFileName());
        file.setFileType(request.getFileType() != null ? request.getFileType() : "LAOFP");
        file.setFileSizeBytes(request.getFileSizeBytes());
        file.setReconciliationDate(request.getReconciliationDate());
        file.setStatus("RECEIVED");
        file.setUploadedBy(request.getUploadedBy());

        file = fileRepository.save(file);

        auditLogService.log("RECON_FILE_RECEIVED", ENTITY, fileRef, SOURCE,
                Map.of("fileRef", fileRef,
                        "fileName", request.getFileName(),
                        "reconDate", request.getReconciliationDate().toString()));
        log.info("Reconciliation file registered: fileRef={} date={}", fileRef, request.getReconciliationDate());
        return file;
    }

    @Transactional(readOnly = true)
    public ReconciliationFileEntity requireFile(String fileRef) {
        return fileRepository.findByFileRef(fileRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation file not found: " + fileRef));
    }

    @Transactional(readOnly = true)
    public List<ReconciliationFileEntity> listByDate(LocalDate date) {
        return fileRepository.findByReconciliationDateOrderByIdDesc(date);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationFileEntity> listByStatus(String status) {
        return fileRepository.findByStatusOrderByReconciliationDateDescIdDesc(status);
    }

    /** Advance file status and update match counters atomically. */
    @Transactional
    public ReconciliationFileEntity updateStatus(String fileRef, String status,
                                                  int matchedCount, int unmatchedCount) {
        ReconciliationFileEntity file = requireFile(fileRef);
        file.setStatus(status);
        file.setMatchedCount(matchedCount);
        file.setUnmatchedCount(unmatchedCount);
        file = fileRepository.save(file);

        auditLogService.log("RECON_FILE_STATUS_UPDATED", ENTITY, fileRef, SOURCE,
                Map.of("fileRef", fileRef, "status", status,
                        "matched", matchedCount, "unmatched", unmatchedCount));
        log.info("Reconciliation file status updated: fileRef={} status={} matched={} unmatched={}",
                fileRef, status, matchedCount, unmatchedCount);
        return file;
    }

    @Transactional
    public ReconciliationFileEntity setTotalRecords(String fileRef, int total) {
        ReconciliationFileEntity file = requireFile(fileRef);
        file.setTotalRecords(total);
        return fileRepository.save(file);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String generateFileRef(LocalDate date) {
        String datePart = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortId  = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RF-" + datePart + "-" + shortId;
    }
}
