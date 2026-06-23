package com.example.switching.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class CriticalDashboardIntegrationTest extends AbstractIntegrationTest {
    @Autowired SettlementDashboardService settlement;
    @Autowired RiskDashboardService risk;
    @Autowired CrossBorderDashboardService crossBorder;

    @Test
    void settlementDashboardReturnsStableEmptyOrLiveShape() {
        var response = settlement.load();
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.summary()).isNotNull();
        assertThat(response.cyclesToday()).isNotNull();
        assertThat(response.topPositions()).isNotNull();
        assertThat(response.recentApprovals()).isNotNull();
    }

    @Test
    void riskDashboardReturnsStableEmptyOrLiveShape() {
        var response = risk.load();
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.summary()).isNotNull();
        assertThat(response.alertsBySeverity()).isNotNull();
        assertThat(response.sanctionsHitsPendingReview()).isNotNull();
        assertThat(response.topRiskParticipants()).isNotNull();
        assertThat(response.aging()).isNotNull();
    }

    @Test
    void crossBorderDashboardIncludesConfiguredCorridorsAndAdapterInventory() {
        var response = crossBorder.load();
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.adapters()).extracting(adapter -> adapter.rail())
                .contains("PROMPTPAY", "BAKONG", "NAPAS", "UPI");
        assertThat(response.currentRates()).isNotEmpty();
        assertThat(response.reconciliation()).isNotNull();
    }
}
