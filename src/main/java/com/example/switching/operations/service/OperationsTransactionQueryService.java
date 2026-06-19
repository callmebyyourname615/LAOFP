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

import com.example.switching.common.util.MaskingUtil;
import com.example.switching.operations.dto.OperationsTransactionItemResponse;
import com.example.switching.operations.dto.OperationsTransactionListResponse;

@Service
public class OperationsTransactionQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsTransactionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsTransactionListResponse searchTransactions(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String transferRef,
            String inquiryRef,
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
                transferRef,
                inquiryRef,
                fromDate,
                toDate,
                params
        );

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t " + whereClause,
                Long.class,
                params.toArray()
        );

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        listParams.add(offset);

        List<OperationsTransactionItemResponse> items = jdbcTemplate.query(
                """
                SELECT
                    t.id,
                    t.transaction_ref,
                    t.inquiry_ref,
                    t.source_bank,
                    t.destination_bank,
                    t.source_account_no,
                    t.destination_account_no,
                    t.amount,
                    t.currency,
                    t.status,
                    t.reference,
                    t.external_reference,
                    t.error_code,
                    t.error_message,
                    t.created_at,
                    t.updated_at
                FROM transactions t
                """
                        + whereClause
                        + """
                        
                        ORDER BY t.created_at DESC, t.id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapRow(rs),
                listParams.toArray()
        );

        return new OperationsTransactionListResponse(
                items.isEmpty() ? "EMPTY" : "HAS_TRANSACTIONS",
                LocalDateTime.now(),
                totalItems == null ? 0L : totalItems,
                items.size(),
                limit,
                offset,
                items
        );
    }

    private String buildWhereClause(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String transferRef,
            String inquiryRef,
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

        if (StringUtils.hasText(status)) {
            conditions.add("t.status = ?");
            params.add(status.trim().toUpperCase());
        }

        if (StringUtils.hasText(transferRef)) {
            conditions.add("t.transaction_ref = ?");
            params.add(transferRef.trim());
        }

        if (StringUtils.hasText(inquiryRef)) {
            conditions.add("t.inquiry_ref = ?");
            params.add(inquiryRef.trim());
        }

        if (StringUtils.hasText(fromDate)) {
            LocalDate parsedFromDate = LocalDate.parse(fromDate.trim());
            conditions.add("t.created_at >= ?");
            params.add(parsedFromDate.atStartOfDay());
        }

        if (StringUtils.hasText(toDate)) {
            LocalDate parsedToDate = LocalDate.parse(toDate.trim());
            conditions.add("t.created_at < ?");
            params.add(parsedToDate.plusDays(1).atStartOfDay());
        }

        if (conditions.isEmpty()) {
            return "";
        }

        return " WHERE " + String.join(" AND ", conditions);
    }

    private OperationsTransactionItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        String transactionRef = rs.getString("transaction_ref");

        return new OperationsTransactionItemResponse(
                rs.getLong("id"),
                transactionRef,
                rs.getString("inquiry_ref"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                MaskingUtil.maskAccount(rs.getString("source_account_no")),
                MaskingUtil.maskAccount(rs.getString("destination_account_no")),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("reference"),
                rs.getString("external_reference"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                "/api/transfers/" + transactionRef + "/trace"
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
