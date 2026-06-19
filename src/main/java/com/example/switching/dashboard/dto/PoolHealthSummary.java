package com.example.switching.dashboard.dto;

import java.math.BigDecimal;

/**
 * Aggregate pool-health snapshot for the operations dashboard.
 *
 * @param totalPools          total number of PSP pool rows
 * @param lowBalanceCount     pools where available_balance &lt; minimum_balance
 * @param totalAvailableLak   sum of available_balance across all pools
 */
public record PoolHealthSummary(
        long       totalPools,
        long       lowBalanceCount,
        BigDecimal totalAvailableLak
) {}
