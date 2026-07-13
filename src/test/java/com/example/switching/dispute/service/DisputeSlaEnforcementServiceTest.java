package com.example.switching.dispute.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DisputeSlaEnforcementServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final DisputeResolutionService resolutionService = org.mockito.Mockito.mock(DisputeResolutionService.class);
    private final DisputeSlaEnforcementService service =
            new DisputeSlaEnforcementService(jdbcTemplate, resolutionService);

    @Test
    void overduePostSettlementDisputeAutoRefunds() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of(
                "dispute_id", 24L,
                "dispute_type", "POST_SETTLEMENT_DESTINATION_DISPUTE",
                "txn_ref", "TRX-SETTLED")));

        int handled = service.checkAndEnforceSlAs();

        org.assertj.core.api.Assertions.assertThat(handled).isEqualTo(1);
        verify(resolutionService).resolve(24L, null, "REFUND",
                "Auto-resolved: SLA deadline exceeded", true);
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void overdueTechnicalErrorWithoutSettledTransferEscalatesForManualReview() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of(
                "dispute_id", 16L,
                "dispute_type", "TECHNICAL_ERROR",
                "txn_ref", "TRX-DRS-REQUIRED")));
        when(jdbcTemplate.queryForObject(contains("transaction_ref"), eq(Integer.class), eq("TRX-DRS-REQUIRED")))
                .thenReturn(0);

        int handled = service.checkAndEnforceSlAs();

        org.assertj.core.api.Assertions.assertThat(handled).isEqualTo(1);
        verify(resolutionService, never()).resolve(any(), any(), any(), any(), eq(true));
        verify(jdbcTemplate).update(anyString(),
                eq("SLA deadline exceeded; dispute requires manual review because the original transfer is not settled"),
                any(),
                eq(16L));
    }
}
