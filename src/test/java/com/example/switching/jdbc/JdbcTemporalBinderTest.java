package com.example.switching.jdbc;

import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JdbcTemporalBinderTest {

    private final PreparedStatement statement = Mockito.mock(PreparedStatement.class);

    @Test
    void bindsInstantAsPostgresTimestamptzInUtc() throws Exception {
        Instant instant = Instant.parse("2026-06-23T12:34:56.123456Z");

        JdbcTemporalBinder.bindTimestamptz(statement, 4, instant);

        verify(statement).setObject(
                4,
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void bindsNullInstantWithExplicitTimestamptzType() throws Exception {
        JdbcTemporalBinder.bindTimestamptz(statement, 2, null);
        verify(statement).setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void bindsLocalTimestampAndDateWithExplicitTypes() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 23, 12, 34, 56, 123_000_000);
        LocalDate date = LocalDate.of(2026, 6, 23);

        JdbcTemporalBinder.bindTimestamp(statement, 5, timestamp);
        JdbcTemporalBinder.bindDate(statement, 6, date);

        verify(statement).setObject(5, timestamp, Types.TIMESTAMP);
        verify(statement).setObject(6, date, Types.DATE);
    }
}
