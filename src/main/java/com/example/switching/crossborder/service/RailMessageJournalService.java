package com.example.switching.crossborder.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RailMessageJournalService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RailMessageJournalService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public UUID recordOutbound(
            String rail,
            String externalReference,
            String internalReference,
            String messageType,
            Object request) {
        return record(
                rail,
                "OUTBOUND",
                externalReference,
                internalReference,
                messageType,
                request,
                "PENDING");
    }

    @Transactional
    public UUID recordInbound(
            String rail,
            String externalReference,
            String internalReference,
            String messageType,
            Object request) {
        return record(
                rail,
                "INBOUND",
                externalReference,
                internalReference,
                messageType,
                request,
                "RECEIVED");
    }

    @Transactional
    public void complete(
            UUID id,
            Object response,
            String status,
            String acknowledgement) {
        try {
            String json = mapper.writeValueAsString(response);
            int updated = jdbc.update("""
                    UPDATE cross_border_rail_message
                       SET response_payload=?::jsonb,
                           response_sha256=?,
                           status=?,
                           acknowledgement_code=?,
                           completed_at=now(),
                           updated_at=now(),
                           attempt_count=attempt_count+1
                     WHERE id=?
                    """, json, sha256(json), status, acknowledgement, id);
            if (updated != 1) {
                throw new IllegalStateException("Rail journal message not found");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot complete rail journal message", exception);
        }
    }

    @Transactional
    public void fail(UUID id, String errorCode) {
        jdbc.update("""
                UPDATE cross_border_rail_message
                   SET status='FAILED',
                       last_error_code=?,
                       attempt_count=attempt_count+1,
                       updated_at=now()
                 WHERE id=?
                """, safeCode(errorCode), id);
    }

    public boolean exists(String rail, String direction, String externalReference) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM cross_border_rail_message
                 WHERE rail=? AND direction=? AND external_ref=?
                """, Integer.class, rail, direction, externalReference);
        return count != null && count > 0;
    }

    private UUID record(
            String rail,
            String direction,
            String externalReference,
            String internalReference,
            String messageType,
            Object request,
            String status) {
        validateIdentity(rail, direction, externalReference, internalReference, messageType);
        try {
            String json = mapper.writeValueAsString(request);
            String requestHash = sha256(json);
            UUID proposedId = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO cross_border_rail_message(
                        id, rail, direction, external_ref, internal_ref,
                        message_type, request_payload, request_sha256,
                        status, received_at, sent_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?,
                        CASE WHEN ?='INBOUND' THEN now() ELSE NULL END,
                        CASE WHEN ?='OUTBOUND' THEN now() ELSE NULL END)
                    ON CONFLICT(rail, direction, external_ref) DO NOTHING
                    """,
                    proposedId,
                    rail,
                    direction,
                    externalReference,
                    internalReference,
                    messageType,
                    json,
                    requestHash,
                    status,
                    direction,
                    direction);

            List<Map<String, Object>> persisted = jdbc.queryForList("""
                    SELECT id, request_sha256, internal_ref, message_type
                      FROM cross_border_rail_message
                     WHERE rail=? AND direction=? AND external_ref=?
                     FOR UPDATE
                    """, rail, direction, externalReference);
            if (persisted.size() != 1) {
                throw new IllegalStateException("Rail journal identity is not unique");
            }
            Map<String, Object> row = persisted.getFirst();
            if (!requestHash.equals(row.get("request_sha256"))
                    || !internalReference.equals(row.get("internal_ref"))
                    || !messageType.equals(row.get("message_type"))) {
                throw new IllegalStateException(
                        "Rail external reference was replayed with different content");
            }
            UUID id = (UUID) row.get("id");
            if (inserted == 0) {
                jdbc.update("""
                        UPDATE cross_border_rail_message
                           SET status=CASE
                               WHEN status IN ('COMPLETED','DECLINED') THEN status
                               ELSE 'REPLAYED'
                           END,
                               updated_at=now()
                         WHERE id=?
                        """, id);
            }
            return id;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot journal rail message", exception);
        }
    }

    private static void validateIdentity(
            String rail,
            String direction,
            String externalReference,
            String internalReference,
            String messageType) {
        if (blank(rail) || blank(direction) || blank(externalReference)
                || blank(internalReference) || blank(messageType)) {
            throw new IllegalArgumentException("Rail journal identity fields are required");
        }
    }

    private static String safeCode(String value) {
        String code = value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return code.substring(0, Math.min(64, code.length()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
