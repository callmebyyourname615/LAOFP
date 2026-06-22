package com.example.switching.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.rtp.dto.CancelRtpRequest;
import com.example.switching.rtp.dto.CreateRtpRequest;
import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.exception.RtpAccessDeniedException;
import com.example.switching.rtp.exception.RtpIdempotencyConflictException;
import com.example.switching.rtp.service.RtpActor;
import com.example.switching.rtp.service.RtpRequestService;

@TestPropertySource(properties = {
        "switching.phase-ii.rtp.enabled=true",
        "switching.phase-ii.rtp.default-expiry=24h",
        "switching.phase-ii.rtp.maximum-expiry=30d"
})
class RtpRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RtpRequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRtpTables() {
        jdbcTemplate.update("DELETE FROM rtp_state_transition");
        jdbcTemplate.update("DELETE FROM rtp_installment_schedule");
        jdbcTemplate.update("DELETE FROM rtp_authorisation");
        jdbcTemplate.update("DELETE FROM rtp_request");
    }

    @Test
    void createReplayQueryAndCancelAreDurableAndIdempotent() {
        String correlationId = "RTP-INT-" + UUID.randomUUID();
        CreateRtpRequest command = command(correlationId, new BigDecimal("150000.00"));
        RtpActor payee = new RtpActor("BANK_A", "BANK_A", false);

        var created = requestService.create(command, payee);
        assertTrue(created.created());
        assertEquals(RtpStatus.PENDING_AUTH, created.response().status());
        assertNotNull(created.response().createdAt());

        var replay = requestService.create(command, payee);
        assertFalse(replay.created());
        assertEquals(created.response().id(), replay.response().id());

        var queriedByPayer = requestService.get(
                created.response().id(),
                new RtpActor("BANK_B", "BANK_B", false));
        assertEquals(created.response().id(), queriedByPayer.id());

        var cancelled = requestService.cancel(
                created.response().id(),
                new CancelRtpRequest("Invoice withdrawn"),
                payee);
        assertEquals(RtpStatus.CANCELLED, cancelled.status());
        assertEquals("Invoice withdrawn", cancelled.cancellationReason());
        assertNotNull(cancelled.cancelledAt());

        var cancelledReplay = requestService.cancel(created.response().id(), null, payee);
        assertEquals(RtpStatus.CANCELLED, cancelledReplay.status());
        assertEquals("Invoice withdrawn", cancelledReplay.cancellationReason());

        Integer transitions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rtp_state_transition WHERE request_id = ?",
                Integer.class,
                created.response().id());
        assertEquals(2, transitions);
    }


    @Test
    void concurrentCreateUsesOneDurableRequestAndOneInitialTransition() throws Exception {
        String correlationId = "RTP-CONCURRENT-" + UUID.randomUUID();
        CreateRtpRequest command = command(correlationId, new BigDecimal("2500.00"));
        RtpActor payee = new RtpActor("BANK_A", "BANK_A", false);
        int workers = 6;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return requestService.create(command, payee).response().id();
                }));
            }
            ready.await();
            start.countDown();

            var ids = new HashSet<UUID>();
            for (Future<UUID> future : futures) {
                ids.add(future.get());
            }
            assertEquals(1, ids.size());
        }

        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rtp_request WHERE request_correlation_id = ?",
                Integer.class,
                correlationId);
        Integer transitionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rtp_state_transition transition_log "
                        + "JOIN rtp_request request ON request.id = transition_log.request_id "
                        + "WHERE request.request_correlation_id = ?",
                Integer.class,
                correlationId);
        assertEquals(1, requestCount);
        assertEquals(1, transitionCount);
    }

    @Test
    void v85SchemaUsesVarcharSha256AndIsRecordedByFlyway() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() "
                        + "AND table_name = 'rtp_request' "
                        + "AND column_name = 'request_fingerprint'",
                String.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '85' AND success",
                Integer.class);

        assertEquals("character varying", dataType);
        assertEquals(1, migrationCount);
    }

    @Test
    void sameCorrelationCanBeUsedByDifferentPayeeParticipants() {
        String correlationId = "RTP-PARTICIPANT-SCOPE-" + UUID.randomUUID();
        var first = requestService.create(
                command(correlationId, new BigDecimal("1000.00")),
                new RtpActor("BANK_A", "BANK_A", false));

        CreateRtpRequest secondCommand = new CreateRtpRequest(
                correlationId,
                "BANK_C",
                "BANK_B",
                "030300000001",
                "020200000001",
                new BigDecimal("1000.00"),
                "LAK",
                "Participant scoped idempotency",
                null);
        var second = requestService.create(
                secondCommand,
                new RtpActor("BANK_C", "BANK_C", false));

        assertFalse(first.response().id().equals(second.response().id()));
        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rtp_request WHERE request_correlation_id = ?",
                Integer.class,
                correlationId);
        assertEquals(2, requestCount);
    }

    @Test
    void sameCorrelationWithDifferentPayloadFailsClosed() {
        String correlationId = "RTP-CONFLICT-" + UUID.randomUUID();
        RtpActor payee = new RtpActor("BANK_A", "BANK_A", false);
        requestService.create(command(correlationId, new BigDecimal("1000.00")), payee);

        assertThrows(RtpIdempotencyConflictException.class,
                () -> requestService.create(command(correlationId, new BigDecimal("1001.00")), payee));
    }

    @Test
    void unrelatedParticipantCannotReadRequest() {
        var created = requestService.create(
                command("RTP-ACCESS-" + UUID.randomUUID(), new BigDecimal("1000.00")),
                new RtpActor("BANK_A", "BANK_A", false));

        assertThrows(RtpAccessDeniedException.class,
                () -> requestService.get(
                        created.response().id(),
                        new RtpActor("BANK_C", "BANK_C", false)));
    }

    private static CreateRtpRequest command(String correlationId, BigDecimal amount) {
        return new CreateRtpRequest(
                correlationId,
                "BANK_A",
                "BANK_B",
                "010100000001",
                "020200000001",
                amount,
                "LAK",
                "Integration invoice",
                null);
    }
}
