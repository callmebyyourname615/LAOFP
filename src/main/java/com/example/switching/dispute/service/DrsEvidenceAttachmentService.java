package com.example.switching.dispute.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentDownload;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentListResponse;
import com.example.switching.dispute.dto.DrsEvidenceAttachmentResponse;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.exception.DrsEvidenceAttachmentInvalidException;
import com.example.switching.dispute.exception.DrsEvidenceAttachmentNotFoundException;

@Service
public class DrsEvidenceAttachmentService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public DrsEvidenceAttachmentService(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DrsEvidenceAttachmentResponse upload(Long disputeId, MultipartFile file, String description, String actor) {
        Map<String, Object> dispute = loadDispute(disputeId);
        if (file == null || file.isEmpty()) {
            throw new DrsEvidenceAttachmentInvalidException("Evidence attachment file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new DrsEvidenceAttachmentInvalidException(
                    "Evidence attachment exceeds max size " + MAX_FILE_SIZE_BYTES + " bytes");
        }

        byte[] payload = bytes(file);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        String sha256 = sha256(payload);
        LocalDateTime now = LocalDateTime.now();

        Long attachmentId;
        try {
            attachmentId = jdbc.queryForObject(
                    """
                    INSERT INTO drs_dispute_attachments
                        (dispute_id, file_name, content_type, file_size_bytes, sha256,
                         uploaded_by, uploaded_at, description, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING attachment_id
                    """,
                    Long.class,
                    disputeId,
                    fileName,
                    contentType,
                    payload.length,
                    sha256,
                    actor,
                    now,
                    description,
                    payload);
        } catch (DataIntegrityViolationException ex) {
            throw new DrsEvidenceAttachmentInvalidException(
                    "Evidence attachment already exists for dispute " + disputeId + " with sha256 " + sha256);
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("disputeId", disputeId);
        auditPayload.put("transferRef", dispute.get("txn_ref"));
        auditPayload.put("attachmentId", attachmentId);
        auditPayload.put("fileName", fileName);
        auditPayload.put("contentType", contentType);
        auditPayload.put("fileSizeBytes", payload.length);
        auditPayload.put("sha256", sha256);
        auditPayload.put("description", nullToEmpty(description));
        auditLogService.log(
                "DRS_EVIDENCE_UPLOADED",
                "DISPUTE",
                string(dispute.get("txn_ref")),
                actor,
                auditPayload);

        return new DrsEvidenceAttachmentResponse(
                attachmentId,
                disputeId,
                fileName,
                contentType,
                (long) payload.length,
                sha256,
                actor,
                now,
                description,
                downloadPath(disputeId, attachmentId));
    }

    @Transactional(readOnly = true)
    public DrsEvidenceAttachmentListResponse list(Long disputeId) {
        loadDispute(disputeId);
        List<DrsEvidenceAttachmentResponse> items = jdbc.query(
                """
                SELECT attachment_id, dispute_id, file_name, content_type, file_size_bytes,
                       sha256, uploaded_by, uploaded_at, description
                  FROM drs_dispute_attachments
                 WHERE dispute_id = ?
                 ORDER BY uploaded_at DESC, attachment_id DESC
                """,
                (rs, rowNum) -> new DrsEvidenceAttachmentResponse(
                        rs.getLong("attachment_id"),
                        rs.getLong("dispute_id"),
                        rs.getString("file_name"),
                        rs.getString("content_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getString("sha256"),
                        rs.getString("uploaded_by"),
                        rs.getTimestamp("uploaded_at").toLocalDateTime(),
                        rs.getString("description"),
                        downloadPath(rs.getLong("dispute_id"), rs.getLong("attachment_id"))),
                disputeId);
        return new DrsEvidenceAttachmentListResponse(disputeId, items.size(), items);
    }

    @Transactional(readOnly = true)
    public DrsEvidenceAttachmentDownload download(Long disputeId, Long attachmentId) {
        loadDispute(disputeId);
        try {
            return jdbc.queryForObject(
                    """
                    SELECT attachment_id, dispute_id, file_name, content_type,
                           file_size_bytes, sha256, payload
                      FROM drs_dispute_attachments
                     WHERE dispute_id = ?
                       AND attachment_id = ?
                    """,
                    (rs, rowNum) -> new DrsEvidenceAttachmentDownload(
                            rs.getLong("attachment_id"),
                            rs.getLong("dispute_id"),
                            rs.getString("file_name"),
                            rs.getString("content_type"),
                            rs.getLong("file_size_bytes"),
                            rs.getString("sha256"),
                            rs.getBytes("payload")),
                    disputeId,
                    attachmentId);
        } catch (EmptyResultDataAccessException ex) {
            throw new DrsEvidenceAttachmentNotFoundException(attachmentId);
        }
    }

    private Map<String, Object> loadDispute(Long disputeId) {
        try {
            return jdbc.queryForMap(
                    "SELECT dispute_id, txn_ref, status FROM disputes WHERE dispute_id = ?",
                    disputeId);
        } catch (EmptyResultDataAccessException ex) {
            throw new DisputeNotFoundException(disputeId);
        }
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new DrsEvidenceAttachmentInvalidException("Unable to read evidence attachment: "
                    + nullToEmpty(ex.getMessage()));
        }
    }

    private String sanitizeFileName(String original) {
        String name = StringUtils.hasText(original) ? original : "evidence.bin";
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", "_");
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && dot > name.length() - 20 ? name.substring(dot) : "";
            name = name.substring(0, Math.min(255 - ext.length(), name.length())) + ext;
        }
        return name.isBlank() ? "evidence.bin" : name;
    }

    private String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception ex) {
            throw new DrsEvidenceAttachmentInvalidException("Unable to hash evidence attachment");
        }
    }

    private String downloadPath(Long disputeId, Long attachmentId) {
        return "/api/operations/disputes/" + disputeId + "/attachments/" + attachmentId + "/download";
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
