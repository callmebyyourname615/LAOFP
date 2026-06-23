package com.example.switching.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.example.switching.AbstractIntegrationTest;
import com.example.switching.dashboard.crossborder.service.CrossBorderDashboardService;
import com.example.switching.dashboard.risk.service.RiskDashboardService;
import com.example.switching.dashboard.settlement.service.SettlementDashboardService;

@TestPropertySource(properties = {
        "switching.smos.enabled=true",
        "switching.smos.jwt-secret=test-smos-jwt-secret-with-more-than-32-characters",
        "switching.smos.bootstrap.enabled=false"
})
class CriticalDashboardDataAcceptanceIntegrationTest extends AbstractIntegrationTest {
    private static final String SETTLEMENT_CYCLE = "P60-DASH-CYCLE";
    private static final String SETTLEMENT_INSTRUCTION = "P60-DASH-INSTRUCTION";
    private static final String RISK_TRANSACTION = "P60-DASH-RISK-TX";
    private static final String VELOCITY_TRANSACTION = "P60-DASH-VELOCITY-TX";
    private static final UUID RAIL_MESSAGE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID RECONCILIATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final String RAIL_EXTERNAL_REF = "P60-DASH-RAIL-EXT";
    private static final String CROSS_BORDER_TXN = "P60-DASH-CB-TX";

    @Autowired JdbcTemplate jdbc;
    @Autowired SettlementDashboardService settlement;
    @Autowired RiskDashboardService risk;
    @Autowired CrossBorderDashboardService crossBorder;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM crossborder_transfers WHERE txn_ref = ?", CROSS_BORDER_TXN);
        jdbc.update("DELETE FROM fx_quotes WHERE source_amount = 600060 AND dest_amount = 156015.6000");
        jdbc.update("DELETE FROM cross_border_rail_reconciliation WHERE id = ?", RECONCILIATION_ID);
        jdbc.update("DELETE FROM cross_border_rail_message WHERE id = ?", RAIL_MESSAGE_ID);
        jdbc.update("DELETE FROM fraud_velocity_decision WHERE transaction_reference = ?", VELOCITY_TRANSACTION);
        jdbc.update("DELETE FROM sanctions_screening_results WHERE txn_id = ?", RISK_TRANSACTION);
        jdbc.update("DELETE FROM fraud_scores WHERE txn_id = ?", RISK_TRANSACTION);
        jdbc.update("DELETE FROM settlement_positions WHERE cycle_id IN (SELECT id FROM settlement_cycles WHERE cycle_ref = ?)", SETTLEMENT_CYCLE);
        jdbc.update("DELETE FROM settlement_instructions WHERE instruction_ref = ?", SETTLEMENT_INSTRUCTION);
        jdbc.update("DELETE FROM settlement_cycles WHERE cycle_ref = ?", SETTLEMENT_CYCLE);
    }

    @Test
    void settlementDashboardAggregatesPendingInstructionsCyclesAndPositions() {
        Long cycleId = jdbc.queryForObject("""
                INSERT INTO settlement_cycles(
                    cycle_ref, settlement_date, cycle_number, status, opened_at)
                VALUES (?, current_date, 32060, 'OPEN', now() - interval '5 hours')
                ON CONFLICT (cycle_ref) DO UPDATE
                SET status = 'OPEN', opened_at = now() - interval '5 hours'
                RETURNING id
                """, Long.class, SETTLEMENT_CYCLE);
        jdbc.update("""
                INSERT INTO settlement_instructions(
                    instruction_ref, cycle_id, debtor_psp_id, creditor_psp_id,
                    currency, net_amount, status)
                VALUES (?, ?, 'BANK_A', 'BANK_B', 'LAK', 250000, 'PENDING_APPROVAL')
                ON CONFLICT (instruction_ref) DO UPDATE
                SET status = 'PENDING_APPROVAL', net_amount = 250000
                """, SETTLEMENT_INSTRUCTION, cycleId);
        jdbc.update("""
                INSERT INTO settlement_positions(
                    cycle_id, bank_code, currency, debit_amount, credit_amount,
                    transaction_count, status)
                VALUES (?, 'BANK_A', 'LAK', 250000, 0, 1, 'OPEN')
                ON CONFLICT (cycle_id, bank_code, currency) DO UPDATE
                SET debit_amount = 250000, credit_amount = 0,
                    transaction_count = 1, status = 'OPEN'
                """, cycleId);

        var response = settlement.load();

        assertThat(response.summary().pendingInstructionCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().pendingInstructionAmount()).isGreaterThanOrEqualTo(new BigDecimal("250000"));
        assertThat(response.summary().lateCyclesToday()).isGreaterThanOrEqualTo(1);
        assertThat(response.cyclesToday()).extracting(item -> item.cycleRef()).contains(SETTLEMENT_CYCLE);
        assertThat(response.topPositions())
                .filteredOn(position -> SETTLEMENT_CYCLE.equals(position.cycleRef()))
                .singleElement()
                .satisfies(position -> {
                    assertThat(position.participantCode()).isEqualTo("BANK_A");
                    assertThat(position.netPosition()).isEqualByComparingTo("-250000");
                });
    }

    @Test
    void riskDashboardAggregatesFraudVelocityAndSanctionsSignals() {
        jdbc.update("""
                INSERT INTO fraud_scores(
                    txn_id, score, risk_tier, signals, action_taken,
                    sending_psp_id, receiving_psp_id, amount)
                VALUES (?, 0.9500, 'CRITICAL', '{"phase60":true}'::jsonb,
                        'BLOCK', 'BANK_A', 'BANK_B', 123000)
                """, RISK_TRANSACTION);
        jdbc.update("""
                INSERT INTO sanctions_screening_results(
                    txn_id, match_score, match_entity, list_type, outcome,
                    screening_ms, debtor_name, creditor_name)
                VALUES (?, 96.50, 'PHASE60 TEST ENTITY', 'BOL',
                        'MANUAL_REVIEW', 5, 'TEST DEBTOR', 'TEST CREDITOR')
                """, RISK_TRANSACTION);
        jdbc.update("""
                INSERT INTO fraud_velocity_decision(
                    transaction_reference, participant_code, subject_key,
                    decision, matched_rules, risk_score, evidence_hash)
                VALUES (?, 'BANK_A', 'phase60-subject', 'HOLD',
                        '["PHASE60"]'::jsonb, 95, ?)
                """, VELOCITY_TRANSACTION, "a".repeat(64));

        var response = risk.load();

        assertThat(response.summary().activeAlerts()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().velocityViolations24h()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().sanctionsHits24h()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().blockedTransactions24h()).isGreaterThanOrEqualTo(1);
        assertThat(response.sanctionsHitsPendingReview())
                .extracting(hit -> hit.transactionId())
                .contains(RISK_TRANSACTION);
        assertThat(response.topRiskParticipants())
                .extracting(item -> item.participantCode())
                .contains("BANK_A");
    }

    @Test
    void crossBorderDashboardAggregatesRailHealthVolumeRatesAndReconciliation() {
        jdbc.update("""
                INSERT INTO cross_border_rail_message(
                    id, rail, direction, external_ref, internal_ref,
                    message_type, status, attempt_count, last_error_code)
                VALUES (?, 'BAKONG', 'OUTBOUND', ?, 'P60-INTERNAL',
                        'PACS008', 'FAILED', 3, 'PHASE60_TEST')
                ON CONFLICT (id) DO UPDATE
                SET status = 'FAILED', updated_at = now()
                """, RAIL_MESSAGE_ID, RAIL_EXTERNAL_REF);
        jdbc.update("""
                INSERT INTO cross_border_rail_reconciliation(
                    id, rail, statement_date, external_ref, internal_ref,
                    external_amount, internal_amount, currency, status,
                    discrepancy_reason, evidence_sha256)
                VALUES (?, 'BAKONG', current_date, ?, 'P60-INTERNAL',
                        100000, 99999, 'LAK', 'AMOUNT_MISMATCH',
                        'phase60 test mismatch', ?)
                ON CONFLICT (id) DO UPDATE SET status = 'AMOUNT_MISMATCH'
                """, RECONCILIATION_ID, RAIL_EXTERNAL_REF, "b".repeat(64));
        Long corridorId = jdbc.queryForObject("""
                SELECT corridor_id FROM fx_corridors
                WHERE source_currency = 'LAK' AND dest_currency = 'VND'
                  AND target_network = 'NAPAS'
                """, Long.class);
        Long quoteId = jdbc.queryForObject("""
                INSERT INTO fx_quotes(
                    corridor_id, source_currency, dest_currency, source_amount,
                    dest_amount, rate, fee, expires_at, used)
                VALUES (?, 'LAK', 'VND', 600060, 156015.6000,
                        0.26000000, 6000.6000, now() + interval '1 hour', true)
                RETURNING quote_id
                """, Long.class, corridorId);
        jdbc.update("""
                INSERT INTO crossborder_transfers(
                    quote_id, txn_ref, initiating_psp_id, purpose_code,
                    source_of_funds, beneficiary_name, beneficiary_bank,
                    beneficiary_account, beneficiary_country, target_network,
                    network_txn_id, status, completed_at)
                VALUES (?, ?, 'BANK_A', 'FAMILY_SUPPORT', 'SALARY',
                        'PHASE60 BENEFICIARY', 'NAPAS TEST BANK', '000600060',
                        'VNM', 'NAPAS', 'P60-NETWORK-TX', 'COMPLETED', now())
                """, quoteId, CROSS_BORDER_TXN);

        var response = crossBorder.load();

        assertThat(response.summary().completedToday()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().failedLastHour()).isGreaterThanOrEqualTo(1);
        assertThat(response.summary().unreconciledItems()).isGreaterThanOrEqualTo(1);
        assertThat(response.adapters())
                .filteredOn(adapter -> adapter.rail().equals("BAKONG"))
                .singleElement()
                .satisfies(adapter -> {
                    assertThat(adapter.status()).isEqualTo("DEGRADED");
                    assertThat(adapter.failedLast24Hours()).isGreaterThanOrEqualTo(1);
                });
        assertThat(response.volumeToday())
                .filteredOn(volume -> volume.network().equals("NAPAS"))
                .singleElement()
                .satisfies(volume -> assertThat(volume.completedCount()).isGreaterThanOrEqualTo(1));
        assertThat(response.currentRates()).isNotEmpty();
        assertThat(response.reconciliation())
                .filteredOn(item -> item.rail().equals("BAKONG") && item.status().equals("AMOUNT_MISMATCH"))
                .singleElement()
                .satisfies(item -> assertThat(item.count()).isGreaterThanOrEqualTo(1));
    }
}
