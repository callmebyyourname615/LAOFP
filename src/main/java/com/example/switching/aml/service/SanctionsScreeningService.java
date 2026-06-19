package com.example.switching.aml.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.aml.config.AmlProperties;
import com.example.switching.aml.dto.ScreeningResult;
import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.exception.ScreeningTimeoutException;
import com.example.switching.aml.exception.ScreeningUnavailableException;
import com.example.switching.aml.sanctions.SanctionsNameNormalizer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Screens original and normalized aliases from the last-known-good sanctions snapshot. */
@Service
public class SanctionsScreeningService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreeningService.class);
    private static final double BLOCK_THRESHOLD = 95.0;
    private static final double REVIEW_THRESHOLD = 70.0;

    private static final String SCREEN_SQL = """
            WITH candidate_names AS (
                SELECT entity_name, list_type, normalized_name AS candidate_name, FALSE AS alias_match
                  FROM sanctions_lists
                 WHERE is_active = TRUE
                UNION ALL
                SELECT live.entity_name, live.list_type, alias.value AS candidate_name, TRUE AS alias_match
                  FROM sanctions_lists live
                  CROSS JOIN LATERAL jsonb_array_elements_text(live.aliases_normalized) AS alias(value)
                 WHERE live.is_active = TRUE
            ), scored AS (
                SELECT entity_name, list_type, alias_match,
                       CASE
                           WHEN candidate_name = ? THEN CASE WHEN alias_match THEN 98.0 ELSE 100.0 END
                           WHEN candidate_name LIKE ? THEN 90.0
                           WHEN candidate_name LIKE ? THEN 75.0
                           ELSE 0.0
                       END AS match_score
                  FROM candidate_names
                 WHERE candidate_name = ?
                    OR candidate_name LIKE ?
                    OR candidate_name LIKE ?
            )
            SELECT entity_name, list_type, alias_match, match_score
              FROM scored
             WHERE match_score >= 70.0
             ORDER BY match_score DESC, alias_match ASC
             LIMIT 1
            """;

    private static final String INSERT_RESULT_SQL = """
            INSERT INTO sanctions_screening_results
                (txn_id, screened_at, match_score, match_entity, list_type, outcome,
                 screening_ms, debtor_name, creditor_name)
            VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AmlProperties amlProperties;
    private final StrGenerationService strGenerationService;
    private final Executor screeningExecutor;
    private final MeterRegistry meterRegistry;
    private final Timer screeningTimer;

    public SanctionsScreeningService(JdbcTemplate jdbcTemplate,
                                     AmlProperties amlProperties,
                                     StrGenerationService strGenerationService,
                                     @Qualifier("sanctionsScreeningExecutor") Executor screeningExecutor,
                                     MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.amlProperties = amlProperties;
        this.strGenerationService = strGenerationService;
        this.screeningExecutor = screeningExecutor;
        this.meterRegistry = meterRegistry;
        this.screeningTimer = Timer.builder("switching.aml.screening.duration")
                .description("Wall-clock time for sanctions screening")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public ScreeningResult screen(String txnId, String debtorName, String creditorName) {
        if (!amlProperties.isScreeningEnabled()) {
            return ScreeningResult.clear(0L);
        }

        long start = Instant.now().toEpochMilli();
        Timer.Sample sample = Timer.start(meterRegistry);
        CompletableFuture<ScreeningResult> future = null;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> doScreen(txnId, debtorName, creditorName, start), screeningExecutor);
            ScreeningResult result = future.get(amlProperties.getScreeningTimeoutMs(), TimeUnit.MILLISECONDS);
            recordOutcome(result.getOutcome());
            if (result.isBlocked()) {
                strGenerationService.generateStrQuietly(txnId, result.getMatchEntity(), result.getListType());
                throw new SanctionsBlockException(result.getMatchEntity(), result.getListType());
            }
            return result;
        } catch (SanctionsBlockException error) {
            throw error;
        } catch (TimeoutException timeout) {
            if (future != null) {
                future.cancel(true);
            }
            long elapsed = elapsed(start);
            persistResult(txnId, null, null, null, "ERROR", elapsed, debtorName, creditorName);
            recordOutcome("timeout");
            throw new ScreeningTimeoutException(elapsed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (future != null) {
                future.cancel(true);
            }
            return handleUnavailable(txnId, debtorName, creditorName, start, interrupted);
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            return handleUnavailable(txnId, debtorName, creditorName, start, cause);
        } finally {
            sample.stop(screeningTimer);
        }
    }

    private ScreeningResult handleUnavailable(String txnId, String debtorName, String creditorName,
                                               long start, Throwable error) {
        long elapsed = elapsed(start);
        persistResult(txnId, null, null, null, "ERROR", elapsed, debtorName, creditorName);
        recordOutcome("error");
        log.error("Sanctions screening unavailable: txn={} elapsedMs={}", txnId, elapsed, error);
        if (amlProperties.isScreeningFailClosed()) {
            throw new ScreeningUnavailableException("Sanctions screening unavailable; transaction not cleared", error);
        }
        log.warn("Sanctions screening configured fail-open: txn={}", txnId);
        return ScreeningResult.clear(elapsed);
    }

    private ScreeningResult doScreen(String txnId, String debtorName, String creditorName, long start) {
        ScreeningResult debtorResult = screenName(txnId, debtorName, debtorName, creditorName, start);
        if (debtorResult != null && !debtorResult.isClear()) {
            return debtorResult;
        }
        ScreeningResult creditorResult = screenName(txnId, creditorName, debtorName, creditorName, start);
        if (creditorResult != null && !creditorResult.isClear()) {
            return creditorResult;
        }
        long elapsed = elapsed(start);
        persistResult(txnId, null, null, null, "CLEAR", elapsed, debtorName, creditorName);
        return ScreeningResult.clear(elapsed);
    }

    private ScreeningResult screenName(String txnId, String name,
                                       String debtorName, String creditorName, long start) {
        String normalized = SanctionsNameNormalizer.normalize(name);
        if (normalized.isBlank()) {
            return null;
        }
        String prefix = normalized + "%";
        String fuzzy = "%" + normalized + "%";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SCREEN_SQL, normalized, prefix, fuzzy, normalized, prefix, fuzzy);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.getFirst();
        String matchEntity = (String) row.get("entity_name");
        String listType = (String) row.get("list_type");
        double matchScore = ((Number) row.get("match_score")).doubleValue();
        long elapsed = elapsed(start);
        String outcome = matchScore >= BLOCK_THRESHOLD ? "BLOCKED" : "MANUAL_REVIEW";
        persistResult(txnId, matchScore, matchEntity, listType, outcome,
                elapsed, debtorName, creditorName);
        return "BLOCKED".equals(outcome)
                ? ScreeningResult.blocked(matchEntity, listType, matchScore, elapsed)
                : ScreeningResult.manualReview(matchEntity, listType, matchScore, elapsed);
    }

    private void persistResult(String txnId, Double matchScore, String matchEntity,
                               String listType, String outcome, long screeningMs,
                               String debtorName, String creditorName) {
        try {
            jdbcTemplate.update(INSERT_RESULT_SQL,
                    txnId, matchScore, matchEntity, listType, outcome,
                    screeningMs, debtorName, creditorName);
        } catch (RuntimeException persistenceError) {
            log.error("Failed to persist sanctions screening result: txn={} outcome={}",
                    txnId, outcome, persistenceError);
        }
    }

    private void recordOutcome(String outcome) {
        meterRegistry.counter("switching.aml.screening.results", "outcome", outcome.toLowerCase()).increment();
    }

    private long elapsed(long start) {
        return Math.max(0, Instant.now().toEpochMilli() - start);
    }
}
