package com.example.switching.consistency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ConsistencyAwareReportingJdbcOperationsTest {

    private final JdbcTemplate primary = mock(JdbcTemplate.class);
    private final JdbcTemplate replica = mock(JdbcTemplate.class);
    private final ReplicaFreshnessProbe probe = mock(ReplicaFreshnessProbe.class);
    private final ConsistencyAwareReportingJdbcOperations operations =
            new ConsistencyAwareReportingJdbcOperations(primary, replica, probe);

    @Test
    void strictAndReadYourWritesAlwaysUsePrimary() {
        assertThat(operations.select(ReadConsistency.STRICT_PRIMARY)).isSameAs(primary);
        assertThat(operations.select(ReadConsistency.READ_YOUR_WRITES)).isSameAs(primary);
    }

    @Test
    void eventualReadUsesReplicaOnlyWhenFresh() {
        when(probe.inspect()).thenReturn(new ReplicaFreshness(
                true, true, Duration.ofMillis(300), Duration.ofSeconds(2),
                Instant.now(), "ok"));

        assertThat(operations.select(ReadConsistency.EVENTUAL)).isSameAs(replica);
    }

    @Test
    void staleOrUnreachableReplicaFailsClosedToPrimary() {
        when(probe.inspect()).thenReturn(new ReplicaFreshness(
                true, true, Duration.ofSeconds(5), Duration.ofSeconds(2),
                Instant.now(), "stale"));
        assertThat(operations.select(ReadConsistency.EVENTUAL)).isSameAs(primary);

        when(probe.inspect()).thenReturn(new ReplicaFreshness(
                false, false, null, Duration.ofSeconds(2),
                Instant.now(), "probe-failed"));
        assertThat(operations.select(ReadConsistency.EVENTUAL)).isSameAs(primary);
    }
}
