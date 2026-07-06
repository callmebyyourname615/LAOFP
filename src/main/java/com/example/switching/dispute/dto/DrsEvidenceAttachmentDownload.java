package com.example.switching.dispute.dto;

public record DrsEvidenceAttachmentDownload(
        Long attachmentId,
        Long disputeId,
        String fileName,
        String contentType,
        Long fileSizeBytes,
        String sha256,
        byte[] payload
) {}
