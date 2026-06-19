package com.example.switching.operations.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.common.util.MaskingUtil;
import com.example.switching.operations.dto.OperationsIsoInquiryItemResponse;
import com.example.switching.operations.dto.OperationsIsoInquiryListResponse;

@Service
public class OperationsIsoInquiryQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsIsoInquiryQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsIsoInquiryListResponse searchIsoInquiries(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String inquiryRef,
            String messageId,
            String instructionId,
            String endToEndId,
            String creditorAccount,
            String usedByTransferRef,
            Boolean expired,
            String fromDate,
            String toDate,
            Integer requestedLimit,
            Integer requestedOffset
    ) {
        int limit = normalizeLimit(requestedLimit);
        int offset = normalizeOffset(requestedOffset);

        List<Object> params = new ArrayList<>();

        String whereClause = buildWhereClause(
                bankCode,
                sourceBank,
                destinationBank,
                status,
                inquiryRef,
                messageId,
                instructionId,
                endToEndId,
                creditorAccount,
                usedByTransferRef,
                expired,
                fromDate,
                toDate,
                params
        );

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inquiries q " + whereClause,
                Long.class,
                params.toArray()
        );

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        listParams.add(offset);

        List<OperationsIsoInquiryItemResponse> items = jdbcTemplate.query(
                """
                SELECT
                    q.id,
                    q.inquiry_ref,
                    q.channel_id,
                    q.message_id,
                    q.instruction_id,
                    q.end_to_end_id,
                    q.source_bank,
                    q.destination_bank,
                    q.debtor_account,
                    q.creditor_account,
                    q.amount,
                    q.currency,
                    q.reference,
                    q.status,
                    q.account_found,
                    q.bank_available,
                    q.eligible_for_transfer,
                    q.error_code,
                    q.error_message,
                    q.expires_at,
                    q.used_by_transaction_ref,
                    q.created_at,
                    q.updated_at
                FROM inquiries q
                """
                        + whereClause
                        + """

                        ORDER BY q.created_at DESC, q.id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapRow(rs),
                listParams.toArray()
        );

        return new OperationsIsoInquiryListResponse(
                items.isEmpty() ? "EMPTY" : "HAS_ISO_INQUIRIES",
                LocalDateTime.now(),
                totalItems == null ? 0L : totalItems,
                items.size(),
                limit,
                offset,
                items
        );
    }

    public Optional<OperationsIsoInquiryItemResponse> findByInquiryRef(String inquiryRef) {
        if (!StringUtils.hasText(inquiryRef)) {
            return Optional.empty();
        }

        OperationsIsoInquiryItemResponse response = jdbcTemplate.query(
                """
                SELECT
                    q.id,
                    q.inquiry_ref,
                    q.channel_id,
                    q.message_id,
                    q.instruction_id,
                    q.end_to_end_id,
                    q.source_bank,
                    q.destination_bank,
                    q.debtor_account,
                    q.creditor_account,
                    q.amount,
                    q.currency,
                    q.reference,
                    q.status,
                    q.account_found,
                    q.bank_available,
                    q.eligible_for_transfer,
                    q.error_code,
                    q.error_message,
                    q.expires_at,
                    q.used_by_transaction_ref,
                    q.created_at,
                    q.updated_at
                FROM inquiries q
                WHERE q.inquiry_ref = ?
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return mapRow(rs);
                },
                inquiryRef.trim()
        );

        return Optional.ofNullable(response);
    }

    private String buildWhereClause(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String inquiryRef,
            String messageId,
            String instructionId,
            String endToEndId,
            String creditorAccount,
            String usedByTransferRef,
            Boolean expired,
            String fromDate,
            String toDate,
            List<Object> params
    ) {
        List<String> conditions = new ArrayList<>();

        if (StringUtils.hasText(bankCode)) {
            String normalizedBankCode = bankCode.trim().toUpperCase();
            conditions.add("(q.source_bank = ? OR q.destination_bank = ?)");
            params.add(normalizedBankCode);
            params.add(normalizedBankCode);
        }

        if (StringUtils.hasText(sourceBank)) {
            conditions.add("q.source_bank = ?");
            params.add(sourceBank.trim().toUpperCase());
        }

        if (StringUtils.hasText(destinationBank)) {
            conditions.add("q.destination_bank = ?");
            params.add(destinationBank.trim().toUpperCase());
        }

        if (StringUtils.hasText(status)) {
            conditions.add("q.status = ?");
            params.add(status.trim().toUpperCase());
        }

        if (StringUtils.hasText(inquiryRef)) {
            conditions.add("q.inquiry_ref = ?");
            params.add(inquiryRef.trim());
        }

        if (StringUtils.hasText(messageId)) {
            conditions.add("q.message_id = ?");
            params.add(messageId.trim());
        }

        if (StringUtils.hasText(instructionId)) {
            conditions.add("q.instruction_id = ?");
            params.add(instructionId.trim());
        }

        if (StringUtils.hasText(endToEndId)) {
            conditions.add("q.end_to_end_id = ?");
            params.add(endToEndId.trim());
        }

        if (StringUtils.hasText(creditorAccount)) {
            conditions.add("q.creditor_account = ?");
            params.add(creditorAccount.trim());
        }

        if (StringUtils.hasText(usedByTransferRef)) {
            conditions.add("q.used_by_transaction_ref = ?");
            params.add(usedByTransferRef.trim());
        }

        if (expired != null) {
            if (expired) {
                conditions.add("q.expires_at IS NOT NULL AND q.expires_at < ?");
            } else {
                conditions.add("(q.expires_at IS NULL OR q.expires_at >= ?)");
            }
            params.add(LocalDateTime.now());
        }

        if (StringUtils.hasText(fromDate)) {
            LocalDate parsedFromDate = LocalDate.parse(fromDate.trim());
            conditions.add("q.created_at >= ?");
            params.add(parsedFromDate.atStartOfDay());
        }

        if (StringUtils.hasText(toDate)) {
            LocalDate parsedToDate = LocalDate.parse(toDate.trim());
            conditions.add("q.created_at < ?");
            params.add(parsedToDate.plusDays(1).atStartOfDay());
        }

        if (conditions.isEmpty()) {
            return "";
        }

        return " WHERE " + String.join(" AND ", conditions);
    }

    private OperationsIsoInquiryItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        String inquiryRef = clean(rs.getString("inquiry_ref"));
        String usedByTransactionRef = clean(rs.getString("used_by_transaction_ref"));
        LocalDateTime expiresAt = toLocalDateTime(rs.getTimestamp("expires_at"));
        boolean expired = expiresAt != null && expiresAt.isBefore(LocalDateTime.now());

        return new OperationsIsoInquiryItemResponse(
                rs.getLong("id"),
                inquiryRef,
                clean(rs.getString("channel_id")),
                clean(rs.getString("message_id")),
                clean(rs.getString("instruction_id")),
                clean(rs.getString("end_to_end_id")),
                clean(rs.getString("source_bank")),
                clean(rs.getString("destination_bank")),

                /*
                 * Current ISO ACMT.023 profile verifies creditor/destination account only.
                 * Keep debtorAccount null in the API response to avoid exposing old dirty rows.
                */
                null,
                MaskingUtil.maskAccount(clean(rs.getString("creditor_account"))),

                rs.getBigDecimal("amount"),
                clean(rs.getString("currency")),
                clean(rs.getString("reference")),
                clean(rs.getString("status")),
                rs.getBoolean("account_found"),
                rs.getBoolean("bank_available"),
                rs.getBoolean("eligible_for_transfer"),
                clean(rs.getString("error_code")),
                clean(rs.getString("error_message")),
                expiresAt,
                expired,
                usedByTransactionRef,
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                "/api/iso-inquiries/" + inquiryRef,
                StringUtils.hasText(usedByTransactionRef) ? "/api/transfers/" + usedByTransactionRef : null
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

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }
}
