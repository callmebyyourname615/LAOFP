package com.example.switching.crossborder.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** PostgreSQL JDBC helpers for unambiguous timestamptz parameters. */
public final class PostgresTemporalBinder {

    private PostgresTemporalBinder() {
    }

    public static void setInstant(PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        if (instant == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        // PostgreSQL JDBC driver cannot bind java.time.Instant directly to TIMESTAMPTZ;
        // convert to OffsetDateTime in UTC first (proven pattern in JdbcTemporalBinder).
        statement.setObject(
                index,
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
