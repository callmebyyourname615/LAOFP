package com.example.switching.continuousassurance.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.switching.continuousassurance.config.ContinuousAssuranceProperties;
import com.example.switching.continuousassurance.dto.ReadinessScorecard;
import com.example.switching.continuousassurance.dto.ReconciliationSnapshot;
import com.example.switching.continuousassurance.dto.SloSnapshot;
import com.example.switching.continuousassurance.model.ReadinessColor;

@Service
@ConditionalOnProperty(prefix = "switching.continuous-assurance", name = "enabled", havingValue = "true")
public class ContinuousReadinessScoringService {
    private final ContinuousAssuranceProperties properties;

    public ContinuousReadinessScoringService(ContinuousAssuranceProperties properties) {
        this.properties = properties;
    }

    public ReadinessScorecard score(SloSnapshot slo, ReconciliationSnapshot recon,
            double backupHealth, double drReadiness, double secretFreshness, double alertHealth) {
        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("availability", percentageScore(slo.availabilityPercent(), properties.getAvailabilitySlo()));
        dimensions.put("paymentSuccess", percentageScore(slo.paymentSuccessPercent(), properties.getPaymentSuccessSlo()));
        dimensions.put("latency", inverseScore(slo.p95LatencyMs(), properties.getP95LatencyMaxMs()));
        dimensions.put("reconciliationFreshness", inverseScore(slo.reconciliationDelayMinutes(), properties.getReconciliationDelayMaxMinutes()));
        dimensions.put("financialIntegrity", recon.financiallyClean() ? 100.0 : 0.0);
        dimensions.put("backupHealth", clamp(backupHealth));
        dimensions.put("drReadiness", clamp(drReadiness));
        dimensions.put("secretFreshness", clamp(secretFreshness));
        dimensions.put("alertHealth", clamp(alertHealth));
        dimensions.put("errorBudget", clamp(slo.errorBudgetRemainingPercent()));

        double score = dimensions.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        boolean financialBlock = !recon.financiallyClean();
        boolean errorBudgetExhausted = slo.errorBudgetRemainingPercent() <= 0.0;
        ReadinessColor color = financialBlock || errorBudgetExhausted || score < properties.getAmberScore() ? ReadinessColor.RED
                : score < properties.getGreenScore() ? ReadinessColor.AMBER : ReadinessColor.GREEN;
        return new ReadinessScorecard(round(score), color, color == ReadinessColor.GREEN && !financialBlock && !errorBudgetExhausted,
                Map.copyOf(dimensions), Instant.now());
    }

    private static double percentageScore(double observed, double target) {
        return clamp((observed / target) * 100.0);
    }
    private static double inverseScore(double observed, double max) {
        if (observed <= max) return 100.0;
        return clamp(100.0 - ((observed - max) / max) * 100.0);
    }
    private static double clamp(double value) { return Math.max(0.0, Math.min(100.0, value)); }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
