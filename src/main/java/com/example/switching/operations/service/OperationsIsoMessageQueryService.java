package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.operations.dto.OperationsIsoMessageItemResponse;
import com.example.switching.operations.dto.OperationsIsoMessageListResponse;

@Service
public class OperationsIsoMessageQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsIsoMessageQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsIsoMessageListResponse searchIsoMessages(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String transferRef,
            String inquiryRef,
            String correlationRef,
            String messageId,
            String endToEndId,
            String messageType,
            String direction,
            String securityStatus,
            String validationStatus,
            String fromDate,
            String toDate,
            Boolean includePayload,
            Integer requestedLimit,
            Integer requestedOffset
    ) {
        int limit = normalizeLimit(requestedLimit);
        int offset = normalizeOffset(requestedOffset);
        boolean shouldIncludePayload = includePayload != null && includePayload;

        List<Object> params = new ArrayList<>();

        String whereClause = buildWhereClause(
                bankCode,
                sourceBank,
                destinationBank,
                transferRef,
                inquiryRef,
                correlationRef,
                messageId,
                endToEndId,
                messageType,
                direction,
                securityStatus,
                validationStatus,
                fromDate,
                toDate,
                params
        );

        String baseFrom = """
                FROM iso_messages i
                LEFT JOIN transactions t ON t.transaction_ref = i.transaction_ref
                """;

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseFrom + whereClause,
                Long.class,
                params.toArray()
        );

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        listParams.add(offset);

        String payloadSelect = """
                    NULL AS plain_payload,
                    NULL AS encrypted_payload,
                  """;

        String sql = """
                SELECT
                    i.id,
                    i.correlation_ref,
                    i.inquiry_ref,
                    i.transaction_ref,
                    t.source_bank,
                    t.destination_bank,
                    i.message_id,
                    i.end_to_end_id,
                    i.message_type,
                    i.direction,
                    i.security_status,
                    i.validation_status,
                    i.error_code,
                    i.error_message,
                    0 AS plain_payload_present,
                    0 AS encrypted_payload_present,
                """
                + payloadSelect
                + """
                    i.created_at
                """
                + baseFrom
                + whereClause
                + """
                
                ORDER BY i.created_at DESC, i.id DESC
                LIMIT ? OFFSET ?
                """;

        List<OperationsIsoMessageItemResponse> items = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRow(rs),
                listParams.toArray()
        );

        return new OperationsIsoMessageListResponse(
                items.isEmpty() ? "EMPTY" : "HAS_ISO_MESSAGES",
                LocalDateTime.now(),
                totalItems == null ? 0L : totalItems,
                items.size(),
                limit,
                offset,
                shouldIncludePayload,
                items
        );
    }

    private String buildWhereClause(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String transferRef,
            String inquiryRef,
            String correlationRef,
            String messageId,
            String endToEndId,
            String messageType,
            String direction,
            String securityStatus,
            String validationStatus,
            String fromDate,
            String toDate,
            List<Object> params
    ) {
        List<String> conditions = new ArrayList<>();

        if (StringUtils.hasText(bankCode)) {
            String normalizedBankCode = bankCode.trim().toUpperCase();
            conditions.add("(t.source_bank = ? OR t.destination_bank = ?)");
            params.add(normalizedBankCode);
            params.add(normalizedBankCode);
        }

        if (StringUtils.hasText(sourceBank)) {
            conditions.add("t.source_bank = ?");
            params.add(sourceBank.trim().toUpperCase());
        }

        if (StringUtils.hasText(destinationBank)) {
            conditions.add("t.destination_bank = ?");
            params.add(destinationBank.trim().toUpperCase());
        }

        if (StringUtils.hasText(transferRef)) {
            conditions.add("i.transaction_ref = ?");
            params.add(transferRef.trim());
        }

        if (StringUtils.hasText(inquiryRef)) {
            conditions.add("i.inquiry_ref = ?");
            params.add(inquiryRef.trim());
        }

        if (StringUtils.hasText(correlationRef)) {
            conditions.add("i.correlation_ref = ?");
            params.add(correlationRef.trim());
        }

        if (StringUtils.hasText(messageId)) {
            conditions.add("i.message_id = ?");
            params.add(messageId.trim());
        }

        if (StringUtils.hasText(endToEndId)) {
            conditions.add("i.end_to_end_id = ?");
            params.add(endToEndId.trim());
        }

        if (StringUtils.hasText(messageType)) {
            conditions.add("i.message_type = ?");
            params.add(messageType.trim().toUpperCase());
        }

        if (StringUtils.hasText(direction)) {
            conditions.add("i.direction = ?");
            params.add(direction.trim().toUpperCase());
        }

        if (StringUtils.hasText(securityStatus)) {
            conditions.add("i.security_status = ?");
            params.add(securityStatus.trim().toUpperCase());
        }

        if (StringUtils.hasText(validationStatus)) {
            conditions.add("i.validation_status = ?");
            params.add(validationStatus.trim().toUpperCase());
        }

        if (StringUtils.hasText(fromDate)) {
            LocalDate parsedFromDate = LocalDate.parse(fromDate.trim());
            conditions.add("i.created_at >= ?");
            params.add(parsedFromDate.atStartOfDay());
        }

        if (StringUtils.hasText(toDate)) {
            LocalDate parsedToDate = LocalDate.parse(toDate.trim());
            conditions.add("i.created_at < ?");
            params.add(parsedToDate.plusDays(1).atStartOfDay());
        }

        if (conditions.isEmpty()) {
            return "";
        }

        return " WHERE " + String.join(" AND ", conditions);
    }

    private OperationsIsoMessageItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        Long id = rs.getLong("id");
        String transactionRef = rs.getString("transaction_ref");

        return new OperationsIsoMessageItemResponse(
                id,
                rs.getString("correlation_ref"),
                rs.getString("inquiry_ref"),
                transactionRef,

                rs.getString("source_bank"),
                rs.getString("destination_bank"),

                rs.getString("message_id"),
                rs.getString("end_to_end_id"),
                rs.getString("message_type"),
                rs.getString("direction"),
                rs.getString("security_status"),
                rs.getString("validation_status"),

                rs.getString("error_code"),
                rs.getString("error_message"),

                rs.getBoolean("plain_payload_present"),
                rs.getBoolean("encrypted_payload_present"),

                rs.getString("plain_payload"),
                rs.getString("encrypted_payload"),

                toLocalDateTime(rs.getTimestamp("created_at")),

                StringUtils.hasText(transactionRef) ? "/api/transfers/" + transactionRef + "/trace" : null,
                "/api/iso-messages/" + id + "/validate"
        );
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private int normalizeOffset(Integer requestedOffset) {
        if (requestedOffset == null || requestedOffset < 0) {
            return 0;
        }

        return requestedOffset;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}