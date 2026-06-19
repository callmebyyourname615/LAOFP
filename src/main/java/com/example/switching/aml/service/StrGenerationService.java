package com.example.switching.aml.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.switching.aml.config.AmlProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages the Suspicious Transaction Report (STR) lifecycle:
 * <ol>
 *   <li>Create a {@code PENDING_SUBMISSION} STR row when a sanctions hit occurs.</li>
 *   <li>Submit PENDING STRs to the BoL FIU every N minutes (scheduler).</li>
 *   <li>Mark rows {@code SUBMITTED} or {@code SUBMISSION_FAILED} based on HTTP response.</li>
 * </ol>
 */
@Profile("!migration")
@Service
public class StrGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StrGenerationService.class);

    private static final String INSERT_SQL = """
            INSERT INTO str_reports (txn_id, triggered_at, status, report_payload, matched_entity, list_type)
            VALUES (?, NOW(), 'PENDING_SUBMISSION', ?::jsonb, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String SELECT_PENDING_SQL = """
            SELECT str_id, txn_id, report_payload
            FROM str_reports
            WHERE status = 'PENDING_SUBMISSION'
              AND retry_count < 24
            ORDER BY triggered_at
            LIMIT 50
            """;

    private static final String MARK_SUBMITTED_SQL = """
            UPDATE str_reports
               SET status = 'SUBMITTED', submitted_at = NOW(), submission_ref = ?, last_error = NULL
             WHERE str_id = ?
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE str_reports
               SET retry_count = retry_count + 1,
                   last_error   = ?,
                   status       = CASE WHEN retry_count + 1 >= 24 THEN 'SUBMISSION_FAILED' ELSE status END
             WHERE str_id = ?
            """;

    private static final String IS_SUBMISSION_FAILED_SQL = """
            SELECT COUNT(*)
            FROM str_reports
            WHERE str_id = ?
              AND status = 'SUBMISSION_FAILED'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AmlProperties amlProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StrGenerationService(JdbcTemplate jdbcTemplate,
                                AmlProperties amlProperties,
                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.amlProperties = amlProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Create a PENDING_SUBMISSION STR row for a sanctions hit.
     * Fire-and-quiet: any DB error is swallowed and logged.
     */
    public void generateStrQuietly(String txnId, String matchedEntity, String listType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("txnId",         txnId);
            payload.put("matchedEntity", matchedEntity);
            payload.put("listType",      listType);
            payload.put("reportedAt",    java.time.LocalDateTime.now().toString());
            payload.put("reportType",    "SANCTIONS_HIT");

            String payloadJson = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update(INSERT_SQL, txnId, payloadJson, matchedEntity, listType);
            log.info("STR created: txn={} entity={} list={}", txnId, matchedEntity, listType);
        } catch (Exception e) {
            log.warn("Failed to create STR: txn={} error={}", txnId, e.getMessage());
        }
    }

    /**
     * Submit PENDING STRs to the BoL FIU.
     * Fired every {@code switching.aml.str-submission-interval-minutes} minutes.
     */
    @Scheduled(fixedDelayString = "#{${switching.aml.str-submission-interval-minutes:5} * 60 * 1000}")
    public void submitPendingStrs() {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(SELECT_PENDING_SQL);
        if (pending.isEmpty()) return;

        log.info("STR submission scheduler: found {} pending STRs", pending.size());
        for (Map<String, Object> row : pending) {
            Long strId       = (Long) row.get("str_id");
            String txnId     = (String) row.get("txn_id");
            String payload   = (String) row.get("report_payload");
            submitSingleStr(strId, txnId, payload);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void submitSingleStr(Long strId, String txnId, String payload) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(amlProperties.getBolFiuUrl() + "/api/str/submit"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", amlProperties.getBolFiuApiKey())
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                // Extract submission_ref from response body if available
                String submissionRef = extractRef(resp.body(), txnId);
                jdbcTemplate.update(MARK_SUBMITTED_SQL, submissionRef, strId);
                log.info("STR submitted: strId={} txn={} ref={}", strId, txnId, submissionRef);
            } else {
                String error = "HTTP " + resp.statusCode() + ": " + resp.body();
                markFailed(strId, txnId, error);
                log.warn("STR submission failed: strId={} txn={} error={}", strId, txnId, error);
            }
        } catch (Exception e) {
            try {
                markFailed(strId, txnId, e.getMessage());
            } catch (Exception dbEx) {
                log.warn("Failed to mark STR as failed: strId={}", strId);
            }
            log.warn("STR submission error: strId={} txn={} error={}", strId, txnId, e.getMessage());
        }
    }

    private void markFailed(Long strId, String txnId, String error) {
        jdbcTemplate.update(MARK_FAILED_SQL, error, strId);
        try {
            Integer finalFailed = jdbcTemplate.queryForObject(IS_SUBMISSION_FAILED_SQL, Integer.class, strId);
            if (finalFailed != null && finalFailed > 0) {
                log.error("STR dead-letter alert: strId={} txn={} status=SUBMISSION_FAILED error={}",
                        strId, txnId, error);
            }
        } catch (Exception ex) {
            log.warn("STR dead-letter status check failed: strId={} txn={} error={}",
                    strId, txnId, ex.getMessage());
        }
    }

    private String extractRef(String responseBody, String txnId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
            Object ref = parsed.get("submissionRef");
            if (ref != null) return ref.toString();
        } catch (Exception ignored) {
            // fall through
        }
        return "FIU-" + txnId;
    }
}
