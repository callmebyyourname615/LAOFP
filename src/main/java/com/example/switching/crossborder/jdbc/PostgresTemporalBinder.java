package com.example.switching.crossborder.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

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
        statement.setObject(index, instant, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
