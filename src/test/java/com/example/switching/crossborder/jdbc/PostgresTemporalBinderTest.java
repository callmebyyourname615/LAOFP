package com.example.switching.crossborder.jdbc;

import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PostgresTemporalBinderTest {

    @Test
    void bindsInstantAsTimestampWithTimezone() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Instant instant = Instant.parse("2026-06-23T10:15:30Z");

        PostgresTemporalBinder.setInstant(statement, 4, instant);

        verify(statement).setObject(4, instant, Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void bindsNullWithTimestampWithTimezoneType() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);

        PostgresTemporalBinder.setInstant(statement, 7, null);

        verify(statement).setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
