package com.example.switching.dashboard.dto;

import java.math.BigDecimal;

/**
 * One data point in the 24-hour transaction trend.
 *
 * @param hour         hour of day (0–23)
 * @param summaryDate  "YYYY-MM-DD" — the calendar date the hour belongs to
 * @param totalCount   total transactions in this hour
 * @param settledCount SETTLED transactions in this hour
 * @param totalAmount  gross LAK volume in this hour
 */
public record HourlyTrendPoint(
        int        hour,
        String     summaryDate,
        long       totalCount,
        long       settledCount,
        BigDecimal totalAmount
) {}
