package com.example.switching.reconciliation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconFileResponse(
        Long id,
        String fileRef,
        String sourceBank,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        LocalDate reconciliationDate,
        String status,
        Integer totalRecords,
        int matchedCount,
        int unmatchedCount,
        String uploadedBy,
        LocalDateTime createdAt
) {}
