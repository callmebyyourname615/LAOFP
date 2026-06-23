package com.example.switching.promotion.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.switching.consistency.ConsistencyAwareReportingJdbcOperations;
import com.example.switching.consistency.ReadConsistency;

class PromotionFunderLedgerReconciliationServiceTest {

    private final ConsistencyAwareReportingJdbcOperations jdbc =
            mock(ConsistencyAwareReportingJdbcOperations.class);
    private final PromotionFunderLedgerReconciliationService service =
            new PromotionFunderLedgerReconciliationService(jdbc);

    @Test
    void balancedPromotionProducesBalancedReport() {
        when(jdbc.queryForList(eq(ReadConsistency.STRICT_PRIMARY), anyString(), any(Object[].class)))
                .thenReturn(List.of(row("20.0000", "20.0000")));

        var report = service.reconcile("BANK_A", "LAK", ReadConsistency.STRICT_PRIMARY);

        assertThat(report.status()).isEqualTo("BALANCED");
        assertThat(report.mismatchCount()).isZero();
        assertThat(report.items()).singleElement()
                .extracting(item -> item.status())
                .isEqualTo("BALANCED");
    }

    @Test
    void settlementCoverageMismatchIsVisibleAndBlocksBalancedStatus() {
        when(jdbc.queryForList(eq(ReadConsistency.STRICT_PRIMARY), anyString(), any(Object[].class)))
                .thenReturn(List.of(row("20.0000", "15.0000")));

        var report = service.reconcile(null, null, ReadConsistency.STRICT_PRIMARY);

        assertThat(report.status()).isEqualTo("MISMATCH");
        assertThat(report.mismatchCount()).isEqualTo(1);
        assertThat(report.items().getFirst().settlementCoverageVariance())
                .isEqualByComparingTo("5.0000");
    }

    private static Map<String, Object> row(String consumedApplications, String settlementRecorded) {
        Map<String, Object> row = new HashMap<>();
        row.put("promotion_id", UUID.randomUUID());
        row.put("promotion_code", "PROMO-A");
        row.put("funder_participant_id", "BANK_A");
        row.put("currency", "LAK");
        row.put("budget_cap", new BigDecimal("100.0000"));
        row.put("budget_reserved", new BigDecimal("10.0000"));
        row.put("budget_consumed", new BigDecimal("20.0000"));
        row.put("reserved_applications", new BigDecimal("10.0000"));
        row.put("consumed_applications", new BigDecimal(consumedApplications));
        row.put("settlement_recorded", new BigDecimal(settlementRecorded));
        row.put("settled_amount", new BigDecimal(settlementRecorded));
        row.put("pending_amount", BigDecimal.ZERO);
        row.put("failed_amount", BigDecimal.ZERO);
        row.put("reversed_amount", BigDecimal.ZERO);
        return row;
    }
}
