package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.switching.AbstractIntegrationTest;

/** Certification coverage for the Phase 62 V104-V106 database controls. */
class V104FinancialPrecisionMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cleanInstallContainsPhase62FinancialPromotionAndTraceControls() {
        // version is VARCHAR so MAX does lexicographic compare ("97" > "106");
        // pick the highest installed_rank instead to get the actual latest applied migration.
        assertThat(jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success "
                        + "ORDER BY installed_rank DESC LIMIT 1", String.class))
                .isEqualTo("106");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(99);

        assertNumericColumn("transactions", "amount", 24, 4);
        assertNumericColumn("settlement_items", "amount", 24, 4);
        assertNumericColumn("settlement_positions", "debit_amount", 24, 4);
        assertNumericColumn("settlement_positions", "credit_amount", 24, 4);
        assertNumericColumn("settlement_positions", "net_position", 24, 4);
        assertNumericColumn("psp_pools", "available_balance", 24, 4);
        assertNumericColumn("fraud_scores", "amount", 24, 4);
        assertNumericColumn("bill_tokens", "bill_amount", 24, 4);
        assertNumericColumn("bill_payments", "amount", 24, 4);

        Map<String, Object> moneyPolicy = jdbc.queryForMap("""
                SELECT precision_digits, scale_digits, rounding_mode
                  FROM financial_precision_policy
                 WHERE domain_name = 'MONEY'
                """);
        assertThat(((Number) moneyPolicy.get("precision_digits")).intValue()).isEqualTo(24);
        assertThat(((Number) moneyPolicy.get("scale_digits")).intValue()).isEqualTo(4);
        assertThat(moneyPolicy.get("rounding_mode")).isEqualTo("HALF_EVEN");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                     'promotion_budget_account',
                     'promotion_budget_reservation',
                     'promotion_funder_ledger')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_proc
                 WHERE proname = 'reserve_promotion_budget'
                """, Integer.class)).isGreaterThanOrEqualTo(1);

        assertTraceColumn("outbox_messages");
        assertTraceColumn("transaction_events");
        assertTraceColumn("audit_logs");
    }

    private void assertNumericColumn(String table, String column, int precision, int scale) {
        Map<String, Object> metadata = jdbc.queryForMap("""
                SELECT numeric_precision, numeric_scale
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = ?
                """, table, column);
        assertThat(((Number) metadata.get("numeric_precision")).intValue()).isEqualTo(precision);
        assertThat(((Number) metadata.get("numeric_scale")).intValue()).isEqualTo(scale);
    }

    private void assertTraceColumn(String table) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = 'trace_id'
                   AND data_type = 'character varying'
                   AND character_maximum_length = 32
                """, Integer.class, table)).isEqualTo(1);
    }
}
