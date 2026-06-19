package com.example.switching.observability;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Profile("!migration")
public class DatabaseLifecycleMetrics {

    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<String> PARTITIONED_TABLES = List.of(
            "payment_flows", "inquiries", "transactions", "transaction_status_history",
            "transaction_events", "iso_messages", "iso_message_payloads", "iso_validation_errors",
            "settlement_items", "reconciliation_items");

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong invalidIndexes = new AtomicLong();
    private final AtomicLong longTransactions = new AtomicLong();
    private final AtomicLong missingPartitionsSevenDays = new AtomicLong();
    private final AtomicLong maximumDeadTupleRatioBasisPoints = new AtomicLong();
    private final AtomicLong maintenanceLastSuccessAgeSeconds = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maintenanceLastRunFailed = new AtomicLong();

    public DatabaseLifecycleMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("switching.database.invalid.indexes", invalidIndexes, AtomicLong::get).register(registry);
        Gauge.builder("switching.database.long.transactions", longTransactions, AtomicLong::get).register(registry);
        Gauge.builder("switching.database.partitions.missing.seven.days", missingPartitionsSevenDays, AtomicLong::get).register(registry);
        Gauge.builder("switching.database.max.dead.tuple.ratio.basis.points", maximumDeadTupleRatioBasisPoints, AtomicLong::get).register(registry);
        Gauge.builder("switching.database.maintenance.last.success.age.seconds", maintenanceLastSuccessAgeSeconds, AtomicLong::get).register(registry);
        Gauge.builder("switching.database.maintenance.last.run.failed", maintenanceLastRunFailed, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${switching.observability.database-lifecycle-refresh:PT5M}")
    public void refresh() {
        invalidIndexes.set(value("SELECT COUNT(*) FROM pg_index WHERE NOT indisvalid"));
        longTransactions.set(value("SELECT COUNT(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid() AND xact_start IS NOT NULL AND now()-xact_start > interval '5 minutes'"));
        Long ratio = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(CASE WHEN n_live_tup+n_dead_tup=0 THEN 0
                  ELSE ROUND((n_dead_tup::numeric/(n_live_tup+n_dead_tup))*10000) END),0)
                FROM pg_stat_user_tables
                """, Long.class);
        maximumDeadTupleRatioBasisPoints.set(ratio == null ? 0 : ratio);
        long missing = 0;
        for (String table : PARTITIONED_TABLES) {
            for (int day = 0; day <= 7; day++) {
                String relation = table + "_" + LocalDate.now().plusDays(day).format(SUFFIX);
                Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, relation);
                if (!Boolean.TRUE.equals(exists)) {
                    missing++;
                }
            }
        }
        missingPartitionsSevenDays.set(missing);
        Long successAge = jdbcTemplate.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MAX(completed_at)))::bigint, 9223372036854775807)
                FROM database_maintenance_runs WHERE status = 'SUCCEEDED'
                """, Long.class);
        maintenanceLastSuccessAgeSeconds.set(successAge == null ? Long.MAX_VALUE : successAge);
        Boolean failed = jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT status = 'FAILED' FROM database_maintenance_runs
                  ORDER BY started_at DESC LIMIT 1), FALSE)
                """, Boolean.class);
        maintenanceLastRunFailed.set(Boolean.TRUE.equals(failed) ? 1 : 0);
    }

    private long value(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
