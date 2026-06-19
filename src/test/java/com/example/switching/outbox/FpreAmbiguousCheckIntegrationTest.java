package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.outbox.service.OutboxProcessorService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class FpreAmbiguousCheckIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK = "BANK_AMB_SRC";
    private static final String DEST_BANK = "BANK_AMB_DST";
    private static final String CONNECTOR_NAME = "HTTP_AMB_CONNECTOR";

    @Autowired private OutboxProcessorService outboxProcessorService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HttpServer server;
    private volatile boolean creditApplied;

    @BeforeEach
    void setUp() throws Exception {
        creditApplied = false;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        LocalDateTime now = LocalDateTime.now();
        for (String bank : List.of(SOURCE_BANK, DEST_BANK)) {
            jdbcTemplate.update("""
                    INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                    ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                    """, bank, bank + " (ambiguous test)", now, now);
        }

        jdbcTemplate.update("""
                INSERT INTO connector_configs (
                    connector_name, bank_code, connector_type, endpoint_url,
                    timeout_ms, enabled, force_reject, created_at, updated_at
                ) VALUES (?, ?, 'HTTP', ?, 5000, true, false, ?, ?)
                ON CONFLICT (connector_name) DO UPDATE SET
                    bank_code = EXCLUDED.bank_code,
                    connector_type = EXCLUDED.connector_type,
                    endpoint_url = EXCLUDED.endpoint_url,
                    enabled = true,
                    force_reject = false,
                    updated_at = EXCLUDED.updated_at
                """, CONNECTOR_NAME, DEST_BANK, baseUrl(), now, now);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void creditAppliedTrue_settlesWithoutSchedulingRetry() {
        creditApplied = true;
        String transferRef = "AMB-APPLIED-" + System.nanoTime();
        long outboxId = insertAmbiguousEvent(transferRef);

        outboxProcessorService.processSingleEvent(outboxId);

        Map<String, Object> outbox = fetchOutbox(outboxId);
        assertEquals("SUCCESS", outbox.get("status"));
        assertEquals(0, num(outbox.get("retry_count")));
        assertFalse(bool(outbox.get("will_retry")));

        Map<String, Object> transfer = fetchTransfer(transferRef);
        assertEquals("SETTLED", transfer.get("status"));
        assertEquals("AMBIGUOUS_CREDIT_CONFIRMED", transfer.get("reference"));
    }

    @Test
    void creditAppliedFalse_schedulesRetry() {
        creditApplied = false;
        String transferRef = "AMB-NOT-APPLIED-" + System.nanoTime();
        long outboxId = insertAmbiguousEvent(transferRef);

        outboxProcessorService.processSingleEvent(outboxId);

        Map<String, Object> outbox = fetchOutbox(outboxId);
        assertEquals("PENDING", outbox.get("status"));
        assertEquals(1, num(outbox.get("retry_count")));
        assertTrue(bool(outbox.get("will_retry")));
        assertEquals("AMBIGUOUS", outbox.get("failure_class"));
        assertNotNull(outbox.get("next_retry_at"));

        Map<String, Object> transfer = fetchTransfer(transferRef);
        assertEquals("ACCEPTED", transfer.get("status"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())
                && exchange.getRequestURI().getPath().contains("/credit-status")) {
            String json = """
                    {"creditApplied":%s,"known":true,"checkedAt":"2026-05-22T00:00:00Z"}
                    """.formatted(creditApplied);
            respond(exchange, 200, "application/json", json);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, "application/xml", "<not-pacs002/>");
            return;
        }

        respond(exchange, 404, "text/plain", "not found");
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private long insertAmbiguousEvent(String transferRef) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no,
                    amount, currency, channel_id, status, business_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, '111100000001', ?, '111200000001',
                          100000.00, 'LAK', 'API', 'ACCEPTED', CURRENT_DATE, ?, ?)
                """, transferRef, transferRef, transferRef, SOURCE_BANK, DEST_BANK, now, now);

        long isoMessageId = insertOutboundIsoMessage(transferRef, now);

        String payload = """
                {"transferRef":"%s","isoMessageId":%d,"sourceBank":"%s","destinationBank":"%s",
                 "debtorAccount":"111100000001","creditorAccount":"111200000001",
                 "amount":100000.00,"currency":"LAK","connectorName":"%s","routeCode":"ROUTE_AMB_TEST"}
                """.formatted(transferRef, isoMessageId, SOURCE_BANK, DEST_BANK, CONNECTOR_NAME);

        jdbcTemplate.update("""
                INSERT INTO outbox_messages (transaction_ref, status, message_type, payload, retry_count, created_at, updated_at)
                VALUES (?, 'PENDING', 'PACS_008', ?, 0, ?, ?)
                """, transferRef, payload, now, now);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);
    }

    private long insertOutboundIsoMessage(String transferRef, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO iso_messages (
                    correlation_ref, transaction_ref, end_to_end_id, message_id,
                    message_type, direction, security_status, validation_status,
                    business_date, created_at
                ) VALUES (?, ?, ?, ?, 'PACS_008', 'OUTBOUND', 'ENCRYPTED', 'NOT_VALIDATED',
                          CURRENT_DATE, ?)
                """, transferRef, transferRef, "E2E-" + transferRef, "MSG-" + transferRef, now);

        Long isoMessageId = jdbcTemplate.queryForObject(
                "SELECT id FROM iso_messages WHERE transaction_ref = ? ORDER BY id DESC LIMIT 1",
                Long.class, transferRef);

        jdbcTemplate.update("""
                INSERT INTO iso_message_payloads (
                    iso_message_id, payload_type, plain_payload, encrypted_payload,
                    payload_size_bytes, business_date, created_at
                ) VALUES (?, 'REQUEST', NULL, ?, ?, CURRENT_DATE, ?)
                """, isoMessageId, "encrypted-payload-" + transferRef, 32, now);

        return isoMessageId;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Map<String, Object> fetchOutbox(long outboxId) {
        return jdbcTemplate.queryForMap("""
                SELECT status, retry_count, next_retry_at, failure_class, will_retry
                  FROM outbox_messages
                 WHERE id = ?
                """, outboxId);
    }

    private Map<String, Object> fetchTransfer(String transferRef) {
        return jdbcTemplate.queryForMap("""
                SELECT status, reference
                  FROM transactions
                 WHERE transaction_ref = ?
                """, transferRef);
    }

    private int num(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private boolean bool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
