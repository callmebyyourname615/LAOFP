package com.example.switching.crossborder.jdbc;

import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PostgresTemporalBinderTest {

    @Test
    void bindsInstantAsTimestampWithTimezone() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Instant instant = Instant.parse("2026-06-23T10:15:30Z");

        PostgresTemporalBinder.setInstant(statement, 4, instant);

        // Bug (e) fix: the helper now converts the Instant to an OffsetDateTime in UTC
        // before delegating to setObject, because the PostgreSQL JDBC driver does not
        // accept a raw java.time.Instant when the SQL type is TIMESTAMP_WITH_TIMEZONE.
        verify(statement).setObject(
                4,
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void bindsNullWithTimestampWithTimezoneType() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);

        PostgresTemporalBinder.setInstant(statement, 7, null);

        verify(statement).setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
