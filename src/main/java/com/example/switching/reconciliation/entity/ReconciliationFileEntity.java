package com.example.switching.reconciliation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents an uploaded reconciliation file.
 *
 * <p>Lifecycle: RECEIVED → PROCESSING → COMPLETED | FAILED
 */
@Entity
@Table(name = "reconciliation_files")
public class ReconciliationFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_ref", nullable = false, unique = true)
    private String fileRef;

    @Column(name = "source_bank")
    private String sourceBank;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** LAOFP | SWIFT | CUSTOM */
    @Column(name = "file_type", nullable = false)
    private String fileType = "LAOFP";

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    /** RECEIVED | PROCESSING | COMPLETED | FAILED */
    @Column(name = "status", nullable = false)
    private String status = "RECEIVED";

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount = 0;

    @Column(name = "unmatched_count", nullable = false)
    private int unmatchedCount = 0;

    /** Path in object storage (MinIO). */
    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getFileRef() { return fileRef; }
    public void setFileRef(String fileRef) { this.fileRef = fileRef; }

    public String getSourceBank() { return sourceBank; }
    public void setSourceBank(String sourceBank) { this.sourceBank = sourceBank; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }

    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }

    public int getUnmatchedCount() { return unmatchedCount; }
    public void setUnmatchedCount(int unmatchedCount) { this.unmatchedCount = unmatchedCount; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
