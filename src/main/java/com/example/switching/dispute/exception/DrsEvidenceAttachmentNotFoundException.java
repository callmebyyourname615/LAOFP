package com.example.switching.dispute.exception;

public class DrsEvidenceAttachmentNotFoundException extends RuntimeException {
    public DrsEvidenceAttachmentNotFoundException(Long attachmentId) {
        super("DRS evidence attachment not found: " + attachmentId);
    }
}
