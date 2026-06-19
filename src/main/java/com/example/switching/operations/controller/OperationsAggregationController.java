package com.example.switching.operations.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.maintenance.service.AggregationService;

/**
 * Ops endpoint to manually trigger aggregation without waiting for cron.
 *
 * <pre>
 *  POST /api/operations/aggregation/run              — aggregate yesterday + today
 *  POST /api/operations/aggregation/run/{date}       — aggregate a specific date
 * </pre>
 */
@RestController
@RequestMapping("/api/operations/aggregation")
public class OperationsAggregationController {

    private final AggregationService aggregationService;

    public OperationsAggregationController(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @PostMapping("/run")
    public ResponseEntity<AggregationResult> runNow() {
        aggregationService.aggregateYesterdayAndToday();
        return ResponseEntity.ok(new AggregationResult("ok", "Aggregated yesterday + today"));
    }

    @PostMapping("/run/{date}")
    public ResponseEntity<AggregationResult> runForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        aggregationService.aggregateForDate(date);
        return ResponseEntity.ok(new AggregationResult("ok", "Aggregated " + date));
    }

    public record AggregationResult(String status, String message) {}
}
