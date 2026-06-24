package com.example.switching.continuousassurance.dto;

import java.time.Instant;
import java.util.Map;

import com.example.switching.continuousassurance.model.ReadinessColor;

public record ReadinessScorecard(
        double score,
        ReadinessColor color,
        boolean releaseAllowed,
        Map<String, Double> dimensions,
        Instant calculatedAt) {
}
