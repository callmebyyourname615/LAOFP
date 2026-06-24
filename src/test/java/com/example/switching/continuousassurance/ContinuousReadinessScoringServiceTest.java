package com.example.switching.continuousassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.switching.continuousassurance.config.ContinuousAssuranceProperties;
import com.example.switching.continuousassurance.dto.ReconciliationSnapshot;
import com.example.switching.continuousassurance.dto.SloSnapshot;
import com.example.switching.continuousassurance.model.ReadinessColor;
import com.example.switching.continuousassurance.service.ContinuousReadinessScoringService;

class ContinuousReadinessScoringServiceTest {
    @Test
    void greenWhenAllDimensionsHealthy() {
        var service = new ContinuousReadinessScoringService(new ContinuousAssuranceProperties());
        var score = service.score(new SloSnapshot(99.99, 100, 250, 1, 95, Instant.now()),
                new ReconciliationSnapshot(100, 200, 0, 0, 0, 0, Instant.now()), 100, 100, 100, 100);
        assertEquals(ReadinessColor.GREEN, score.color());
        assertTrue(score.releaseAllowed());
    }

    @Test
    void financialMismatchAlwaysRed() {
        var service = new ContinuousReadinessScoringService(new ContinuousAssuranceProperties());
        var score = service.score(new SloSnapshot(100, 100, 100, 0, 100, Instant.now()),
                new ReconciliationSnapshot(100, 200, 1, 0, 0, 0, Instant.now()), 100, 100, 100, 100);
        assertEquals(ReadinessColor.RED, score.color());
        assertFalse(score.releaseAllowed());
    }
    @Test
    void exhaustedErrorBudgetAlwaysRed() {
        var service = new ContinuousReadinessScoringService(new ContinuousAssuranceProperties());
        var score = service.score(new SloSnapshot(100, 100, 100, 0, 0, Instant.now()),
                new ReconciliationSnapshot(100, 200, 0, 0, 0, 0, Instant.now()), 100, 100, 100, 100);
        assertEquals(ReadinessColor.RED, score.color());
        assertFalse(score.releaseAllowed());
    }

}
