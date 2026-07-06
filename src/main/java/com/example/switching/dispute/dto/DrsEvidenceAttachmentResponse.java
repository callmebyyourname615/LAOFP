package com.example.switching.dispute.dto;

import java.time.LocalDateTime;

public record DrsEvidenceAttachmentResponse(
        Long attachmentId,
        Long disputeId,
        String fileName,
        String contentType,
        Long fileSizeBytes,
        String sha256,
        String uploadedBy,
        LocalDateTime uploadedAt,
        String description,
        String downloadPath
) {}
