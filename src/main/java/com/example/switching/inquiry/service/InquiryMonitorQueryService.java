package com.example.switching.inquiry.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.switching.inquiry.dto.InquiryMonitorItemResponse;
import com.example.switching.inquiry.dto.InquiryMonitorListResponse;

@Service
public class InquiryMonitorQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public InquiryMonitorQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InquiryMonitorListResponse searchInquiries(
            String status,
            String sourceBank,
            String destinationBank,
            String inquiryRef,
            String clientInquiryId,
            String creditorAccount,
            String currency,
            String fromDate,
            String toDate,
            Integer limit,
            Integer offset
    ) {
        int safeLimit = normalizeLimit(limit);
        int safeOffset = normalizeOffset(offset);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        addEqualsFilter(where, params, "status", status);
        addEqualsFilter(where, params, "source_bank", sourceBank);
        addEqualsFilter(where, params, "destination_bank", destinationBank);
        addEqualsFilter(where, params, "inquiry_ref", inquiryRef);
        addEqualsFilter(where, params, "client_inquiry_id", clientInquiryId);
        addEqualsFilter(where, params, "creditor_account", creditorAccount);
        addEqualsFilter(where, params, "currency", currency);

        LocalDateTime from = parseDateTimeStart(fromDate);
        if (from != null) {
            where.append(" AND created_at >= ? ");
            params.add(Timestamp.valueOf(from));
        }

        LocalDateTime toExclusive = parseDateTimeEndExclusive(toDate);
        if (toExclusive != null) {
            where.append(" AND created_at < ? ");
            params.add(Timestamp.valueOf(toExclusive));
        }

        String countSql = """
                SELECT COUNT(*)
                FROM inquiries
                """ + where;

        Long totalItems = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                params.toArray()
        );

        String dataSql = """
                SELECT
                    id,
                    inquiry_ref,
                    client_inquiry_id,
                    source_bank,
                    destination_bank,
                    creditor_account,
                    amount,
                    currency,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    destination_account_name,
                    message,
                    reference,
                    created_at,
                    updated_at
                FROM inquiries
                """ + where + """
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(safeOffset);

        List<InquiryMonitorItemResponse> items = jdbcTemplate.query(
                dataSql,
                (rs, rowNum) -> mapInquiry(rs),
                dataParams.toArray()
        );

        return InquiryMonitorListResponse.of(
                totalItems == null ? 0L : totalItems,
                safeLimit,
                safeOffset,
                items
        );
    }

    public InquiryMonitorItemResponse getInquiryByRef(String inquiryRef) {
        if (!StringUtils.hasText(inquiryRef)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing inquiryRef");
        }

        String sql = """
                SELECT
                    id,
                    inquiry_ref,
                    client_inquiry_id,
                    source_bank,
                    destination_bank,
                    creditor_account,
                    amount,
                    currency,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    destination_account_name,
                    message,
                    reference,
                    created_at,
                    updated_at
                FROM inquiries
                WHERE inquiry_ref = ?
                LIMIT 1
                """;

        List<InquiryMonitorItemResponse> items = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapInquiry(rs),
                inquiryRef
        );

        if (items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Inquiry not found: " + inquiryRef
            );
        }

        return items.get(0);
    }

    private static InquiryMonitorItemResponse mapInquiry(ResultSet rs) throws SQLException {
        return new InquiryMonitorItemResponse(
                rs.getLong("id"),
                rs.getString("inquiry_ref"),
                rs.getString("client_inquiry_id"),
                rs.getString("source_bank"),
                rs.getString("destination_bank"),
                rs.getString("creditor_account"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                getBoolean(rs, "account_found"),
                getBoolean(rs, "bank_available"),
                getBoolean(rs, "eligible_for_transfer"),
                rs.getString("destination_account_name"),
                rs.getString("message"),
                rs.getString("reference"),
                getLocalDateTime(rs, "created_at"),
                getLocalDateTime(rs, "updated_at")
        );
    }

    private static void addEqualsFilter(
            StringBuilder where,
            List<Object> params,
            String columnName,
            String value
    ) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        where.append(" AND ").append(columnName).append(" = ? ");
        params.add(value.trim());
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private static int normalizeOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }

        return offset;
    }

    private static LocalDateTime parseDateTimeStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }

        return LocalDateTime.parse(trimmed);
    }

    private static LocalDateTime parseDateTimeEndExclusive(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).plusDays(1).atStartOfDay();
        }

        return LocalDateTime.parse(trimmed);
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }

        return Boolean.parseBoolean(value.toString());
    }
}