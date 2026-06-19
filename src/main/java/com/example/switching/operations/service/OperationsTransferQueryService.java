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
import com.example.switching.operations.dto.OperationsTransferItemResponse;
import com.example.switching.operations.dto.OperationsTransferListResponse;

@Service
public class OperationsTransferQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OperationsTransferQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationsTransferListResponse searchTransfers(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String transferRef,
            String inquiryRef,
            String channelId,
            String routeCode,
            String connectorName,
            String debtorAccount,
            String creditorAccount,
            String externalReference,
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
                channelId,
                routeCode,
                connectorName,
                debtorAccount,
                creditorAccount,
                externalReference,
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

        List<OperationsTransferItemResponse> items = jdbcTemplate.query(
                """
                SELECT
                    t.id,
                    t.transaction_ref,
                    t.client_transaction_id,
                    t.inquiry_ref,
                    t.source_bank,
                    t.source_account_no,
                    t.destination_bank,
                    t.destination_account_no,
                    t.destination_account_name,
                    t.amount,
                    t.currency,
                    t.status,
                    t.channel_id,
                    t.route_code,
                    t.connector_name,
                    t.external_reference,
                    t.reference,
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

        return new OperationsTransferListResponse(
                items.isEmpty() ? "EMPTY" : "HAS_TRANSFERS",
                LocalDateTime.now(),
                totalItems == null ? 0L : totalItems,
                items.size(),
                limit,
                offset,
                items
        );
    }

    public Optional<OperationsTransferItemResponse> findByTransferRef(String transferRef) {
        if (!StringUtils.hasText(transferRef)) {
            return Optional.empty();
        }

        OperationsTransferItemResponse response = jdbcTemplate.query(
                """
                SELECT
                    t.id,
                    t.transaction_ref,
                    t.client_transaction_id,
                    t.inquiry_ref,
                    t.source_bank,
                    t.source_account_no,
                    t.destination_bank,
                    t.destination_account_no,
                    t.destination_account_name,
                    t.amount,
                    t.currency,
                    t.status,
                    t.channel_id,
                    t.route_code,
                    t.connector_name,
                    t.external_reference,
                    t.reference,
                    t.error_code,
                    t.error_message,
                    t.created_at,
                    t.updated_at
                FROM transactions t
                WHERE t.transaction_ref = ?
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return mapRow(rs);
                },
                transferRef.trim()
        );

        return Optional.ofNullable(response);
    }

    private String buildWhereClause(
            String bankCode,
            String sourceBank,
            String destinationBank,
            String status,
            String transferRef,
            String inquiryRef,
            String channelId,
            String routeCode,
            String connectorName,
            String debtorAccount,
            String creditorAccount,
            String externalReference,
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

        if (StringUtils.hasText(channelId)) {
            conditions.add("t.channel_id = ?");
            params.add(channelId.trim());
        }

        if (StringUtils.hasText(routeCode)) {
            conditions.add("t.route_code = ?");
            params.add(routeCode.trim());
        }

        if (StringUtils.hasText(connectorName)) {
            conditions.add("t.connector_name = ?");
            params.add(connectorName.trim());
        }

        if (StringUtils.hasText(debtorAccount)) {
            conditions.add("t.source_account_no = ?");
            params.add(debtorAccount.trim());
        }

        if (StringUtils.hasText(creditorAccount)) {
            conditions.add("t.destination_account_no = ?");
            params.add(creditorAccount.trim());
        }

        if (StringUtils.hasText(externalReference)) {
            conditions.add("t.external_reference = ?");
            params.add(externalReference.trim());
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

    private OperationsTransferItemResponse mapRow(ResultSet rs) throws java.sql.SQLException {
        String transactionRef = clean(rs.getString("transaction_ref"));
        String status = clean(rs.getString("status"));
        String inquiryRef = clean(rs.getString("inquiry_ref"));

        return new OperationsTransferItemResponse(
                rs.getLong("id"),
                transactionRef,
                clean(rs.getString("client_transaction_id")),
                inquiryRef,
                clean(rs.getString("source_bank")),
                MaskingUtil.maskAccount(clean(rs.getString("source_account_no"))),
                clean(rs.getString("destination_bank")),
                MaskingUtil.maskAccount(clean(rs.getString("destination_account_no"))),
                clean(rs.getString("destination_account_name")),
                rs.getBigDecimal("amount"),
                clean(rs.getString("currency")),
                status,
                status,
                clean(rs.getString("channel_id")),
                clean(rs.getString("route_code")),
                clean(rs.getString("connector_name")),
                clean(rs.getString("external_reference")),
                clean(rs.getString("reference")),
                clean(rs.getString("error_code")),
                clean(rs.getString("error_message")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                "/api/transfers/" + transactionRef,
                "/api/transfers/" + transactionRef + "/trace",
                StringUtils.hasText(inquiryRef) ? "/api/iso-inquiries/" + inquiryRef : null
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
