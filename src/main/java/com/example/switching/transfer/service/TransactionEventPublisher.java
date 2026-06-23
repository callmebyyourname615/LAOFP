package com.example.switching.transfer.service;

import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.switching.observability.tracing.TraceContextSupport;

/**
 * Publishes lifecycle events into the partitioned {@code transaction_events} table.
 *
 * <p>All methods are "fire-and-quiet" — exceptions are caught and logged as warnings
 * so that event publishing never breaks the calling transaction.
 *
 * <p>Event types used in this system:
 * <ul>
 *   <li>{@code TRANSFER_INITIATED}   — transfer record created (status=ACCEPTED)</li>
 *   <li>{@code TRANSFER_DISPATCHED}  — outbox worker dispatching to destination bank</li>
 *   <li>{@code TRANSFER_SETTLED}     — destination bank confirmed</li>
 *   <li>{@code TRANSFER_REJECTED}    — terminal failure, no more retries</li>
 *   <li>{@code TRANSFER_RETRY_SCHEDULED} — retry queued after transient failure</li>
 *   <li>{@code INQUIRY_CREATED}      — inquiry record accepted</li>
 * </ul>
 */
@Component
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private static final String INSERT_SQL = """
            INSERT INTO transaction_events
                (transaction_ref, event_type, payload, actor, business_date, trace_id)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContext;

    public TransactionEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                     TraceContextSupport traceContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceContext = traceContext;
    }

    /**
     * Publish an event — never throws.
     *
     * @param transactionRef the transfer or inquiry ref
     * @param eventType      e.g. {@code TRANSFER_SETTLED}
     * @param businessDate   partition key — must match the transfer's business_date
     * @param payload        free-form metadata (serialised to JSONB)
     * @param actor          who/what caused this event (e.g. {@code "API"}, {@code "WORKER"})
     */
    public void publishQuietly(String transactionRef,
                                String eventType,
                                LocalDate businessDate,
                                Map<String, Object> payload,
                                String actor) {
        try {
            String payloadJson = toJson(payload);
            jdbcTemplate.update(INSERT_SQL, transactionRef, eventType, payloadJson, actor, businessDate,
                    traceContext.currentTraceId().orElse(null));
        } catch (Exception ex) {
            log.warn("Failed to publish transaction event txnRef={} type={} — {}",
                    transactionRef, eventType, ex.getMessage());
        }
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise event payload — using empty JSON: {}", ex.getMessage());
            return "{}";
        }
    }
}
