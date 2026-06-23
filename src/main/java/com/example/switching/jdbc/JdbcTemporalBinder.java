package com.example.switching.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Explicit JDBC temporal binding for PostgreSQL.
 *
 * <p>Do not pass {@link Instant} through JdbcTemplate varargs because the
 * PostgreSQL driver cannot always infer whether the target is timestamp or
 * timestamptz. Callers must select the database type intentionally.</p>
 */
public final class JdbcTemporalBinder {

    private JdbcTemporalBinder() {
    }

    public static void bindTimestamptz(
            PreparedStatement statement,
            int index,
            Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(
                index,
                OffsetDateTime.ofInstant(value, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    public static void bindTimestamp(
            PreparedStatement statement,
            int index,
            LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
            return;
        }
        statement.setObject(index, value, Types.TIMESTAMP);
    }

    public static void bindDate(
            PreparedStatement statement,
            int index,
            LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
            return;
        }
        statement.setObject(index, value, Types.DATE);
    }
}
