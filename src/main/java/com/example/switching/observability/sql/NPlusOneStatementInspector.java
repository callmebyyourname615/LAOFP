package com.example.switching.observability.sql;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Request-scoped SQL fingerprint collector used only when explicitly enabled.
 * SQL literals are normalised so logs contain no account numbers or payment payloads.
 */
public class NPlusOneStatementInspector implements StatementInspector {
    private static final Pattern QUOTED = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern NUMBER = Pattern.compile("(?<![a-zA-Z_])\\d+(?:\\.\\d+)?");
    private static final ThreadLocal<Stats> STATS = new ThreadLocal<>();
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static volatile int queryWarningThreshold = 25;
    private static volatile int repeatedStatementThreshold = 5;

    public static void configure(boolean enabled, int queryThreshold, int repeatedThreshold) {
        ENABLED.set(enabled);
        queryWarningThreshold = Math.max(1, queryThreshold);
        repeatedStatementThreshold = Math.max(2, repeatedThreshold);
    }

    public static boolean enabled() { return ENABLED.get(); }
    public static int queryWarningThreshold() { return queryWarningThreshold; }
    public static int repeatedStatementThreshold() { return repeatedStatementThreshold; }

    public static void beginRequest() {
        if (ENABLED.get()) STATS.set(new Stats());
    }

    public static Snapshot endRequest() {
        Stats stats = STATS.get();
        STATS.remove();
        return stats == null ? new Snapshot(0, Map.of()) : stats.snapshot();
    }

    @Override
    public String inspect(String sql) {
        if (!ENABLED.get() || sql == null) return sql;
        Stats stats = STATS.get();
        if (stats != null) stats.record(fingerprint(sql));
        return sql;
    }

    static String fingerprint(String sql) {
        String normalized = QUOTED.matcher(sql).replaceAll("?");
        normalized = NUMBER.matcher(normalized).replaceAll("?");
        normalized = normalized.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }

    public record Snapshot(int totalQueries, Map<String, Integer> statementCounts) {
        public int maxRepeatedCount() {
            return statementCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        }
        public String mostRepeatedFingerprint() {
            return statementCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        }
    }

    private static final class Stats {
        private int total;
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        void record(String fingerprint) {
            total++;
            counts.merge(fingerprint, 1, Integer::sum);
        }
        Snapshot snapshot() { return new Snapshot(total, Map.copyOf(counts)); }
    }
}
