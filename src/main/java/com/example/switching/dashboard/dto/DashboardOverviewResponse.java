package com.example.switching.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enriched dashboard overview returned by {@code GET /api/dashboard/overview}.
 *
 * <p>Fields are split into four sections:
 * <ol>
 *   <li><b>Today's aggregation</b> — sourced from {@code daily_transaction_summary}</li>
 *   <li><b>Hourly trend</b> — last 24 h from {@code hourly_transaction_summary}</li>
 *   <li><b>Pool health</b> — live view of {@code psp_pools}</li>
 *   <li><b>Status counts</b> — live JPA counts (original fields, kept for backwards compat)</li>
 * </ol>
 */
public class DashboardOverviewResponse {

    private LocalDateTime generatedAt;

    // ── Today's aggregation ──────────────────────────────────────────────────
    private long       todayTotalCount;
    private long       todaySettledCount;
    private long       todayRejectedCount;
    private BigDecimal todayTotalVolumeLak;
    private BigDecimal todaySettledVolumeLak;
    /** (todaySettledCount / todayTotalCount) × 100, or 0 when no transactions. */
    private double     todaySuccessRate;

    // ── 24-hour hourly trend ─────────────────────────────────────────────────
    private List<HourlyTrendPoint> hourlyTrend;

    // ── Pool health ──────────────────────────────────────────────────────────
    private PoolHealthSummary poolHealth;

    // ── Active work items ────────────────────────────────────────────────────
    private long openDisputeCount;
    private long pendingOutboxCount;

    // ── Legacy status-count fields (backwards compatible) ────────────────────
    private long                      inquiriesTotal;
    private List<StatusCountResponse> inquiryStatusCounts;
    private long                      transfersTotal;
    private List<StatusCountResponse> transferStatusCounts;
    private long                      outboxEventsTotal;
    private List<StatusCountResponse> outboxStatusCounts;

    public DashboardOverviewResponse() {}

    // ── getters / setters ─────────────────────────────────────────────────────

    public LocalDateTime getGeneratedAt()              { return generatedAt; }
    public void          setGeneratedAt(LocalDateTime v){ this.generatedAt = v; }

    public long       getTodayTotalCount()              { return todayTotalCount; }
    public void       setTodayTotalCount(long v)        { this.todayTotalCount = v; }

    public long       getTodaySettledCount()            { return todaySettledCount; }
    public void       setTodaySettledCount(long v)      { this.todaySettledCount = v; }

    public long       getTodayRejectedCount()           { return todayRejectedCount; }
    public void       setTodayRejectedCount(long v)     { this.todayRejectedCount = v; }

    public BigDecimal getTodayTotalVolumeLak()              { return todayTotalVolumeLak; }
    public void       setTodayTotalVolumeLak(BigDecimal v)  { this.todayTotalVolumeLak = v; }

    public BigDecimal getTodaySettledVolumeLak()              { return todaySettledVolumeLak; }
    public void       setTodaySettledVolumeLak(BigDecimal v)  { this.todaySettledVolumeLak = v; }

    public double getTodaySuccessRate()          { return todaySuccessRate; }
    public void   setTodaySuccessRate(double v)  { this.todaySuccessRate = v; }

    public List<HourlyTrendPoint> getHourlyTrend()                     { return hourlyTrend; }
    public void                   setHourlyTrend(List<HourlyTrendPoint> v){ this.hourlyTrend = v; }

    public PoolHealthSummary getPoolHealth()               { return poolHealth; }
    public void              setPoolHealth(PoolHealthSummary v){ this.poolHealth = v; }

    public long getOpenDisputeCount()            { return openDisputeCount; }
    public void setOpenDisputeCount(long v)      { this.openDisputeCount = v; }

    public long getPendingOutboxCount()          { return pendingOutboxCount; }
    public void setPendingOutboxCount(long v)    { this.pendingOutboxCount = v; }

    public long getInquiriesTotal()              { return inquiriesTotal; }
    public void setInquiriesTotal(long v)        { this.inquiriesTotal = v; }

    public List<StatusCountResponse> getInquiryStatusCounts()                         { return inquiryStatusCounts; }
    public void                      setInquiryStatusCounts(List<StatusCountResponse> v){ this.inquiryStatusCounts = v; }

    public long getTransfersTotal()              { return transfersTotal; }
    public void setTransfersTotal(long v)        { this.transfersTotal = v; }

    public List<StatusCountResponse> getTransferStatusCounts()                         { return transferStatusCounts; }
    public void                      setTransferStatusCounts(List<StatusCountResponse> v){ this.transferStatusCounts = v; }

    public long getOutboxEventsTotal()           { return outboxEventsTotal; }
    public void setOutboxEventsTotal(long v)     { this.outboxEventsTotal = v; }

    public List<StatusCountResponse> getOutboxStatusCounts()                         { return outboxStatusCounts; }
    public void                      setOutboxStatusCounts(List<StatusCountResponse> v){ this.outboxStatusCounts = v; }
}
