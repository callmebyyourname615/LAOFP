package com.example.switching.promotion.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.consistency.ReadConsistency;

class PromotionFunderLedgerReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionFunderLedgerReconciliationService service;

    private UUID promotionId;
    private UUID applicationId;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM promotion_settlement");
        jdbc.update("DELETE FROM promotion_application");
        jdbc.update("DELETE FROM promotion_eligibility_rule");
        jdbc.update("DELETE FROM promotion");
        promotionId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO promotion(
                    id, code, name, promotion_type, status, priority, combinable,
                    funder_participant_id, currency, budget_cap, budget_reserved,
                    budget_consumed, discount_value, discount_mode, starts_at,
                    ends_at, created_by)
                VALUES (?, ?, 'Phase 70', 'WAIVER', 'ACTIVE', 100, false,
                        'BANK_A', 'LAK', 100, 0, 20, 10, 'FIXED',
                        now() - interval '1 minute', now() + interval '1 day', 'test')
                """, promotionId, "PH70-" + promotionId);
        jdbc.update("""
                INSERT INTO promotion_application(
                    id, promotion_id, transaction_reference, participant_id,
                    channel, gross_fee, discount_amount, net_fee, currency,
                    status, eligibility_evidence, evidence_sha256, consumed_at)
                VALUES (?, ?, ?, 'BANK_B', 'API', 50, 20, 30, 'LAK',
                        'CONSUMED', '{}'::jsonb, ?, now())
                """, applicationId, promotionId, "TX-" + applicationId, "a".repeat(64));
        jdbc.update("""
                INSERT INTO promotion_settlement(
                    id, promotion_application_id, funder_participant_id,
                    beneficiary_participant_id, amount, currency,
                    settlement_reference, status, settled_at)
                VALUES (?, ?, 'BANK_A', 'BANK_B', 20, 'LAK', ?, 'SETTLED', now())
                """, UUID.randomUUID(), applicationId, "SET-" + applicationId);
    }

    @Test
    void reportsBalancedThenDetectsBudgetMismatch() {
        var balanced = service.reconcile("BANK_A", "LAK", ReadConsistency.STRICT_PRIMARY);
        assertThat(balanced.status()).isEqualTo("BALANCED");

        jdbc.update("UPDATE promotion SET budget_consumed = 19 WHERE id = ?", promotionId);
        var mismatch = service.reconcile("BANK_A", "LAK", ReadConsistency.STRICT_PRIMARY);
        assertThat(mismatch.status()).isEqualTo("MISMATCH");
        assertThat(mismatch.items().getFirst().consumptionVariance())
                .isEqualByComparingTo("-1.0000");
    }
}
