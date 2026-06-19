package com.example.switching.qr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the QR Code Service (P15).
 *
 * <pre>
 *   switching.qr.sla-ms=10000
 *   switching.qr.dynamic-max-expiry-minutes=1440
 *   switching.qr.refund-window-days=30
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "switching.qr")
public class QrProperties {

    /** Maximum SLA for QR payment end-to-end in milliseconds. */
    private long slaMs = 10_000L;

    /** Maximum allowed expiry for DYNAMIC QR codes (minutes). */
    private int dynamicMaxExpiryMinutes = 1440;   // 24 hours

    /** Window within which a QR payment can be refunded (days). */
    private int refundWindowDays = 30;

    public long getSlaMs()                              { return slaMs; }
    public void setSlaMs(long v)                        { this.slaMs = v; }
    public int getDynamicMaxExpiryMinutes()              { return dynamicMaxExpiryMinutes; }
    public void setDynamicMaxExpiryMinutes(int v)        { this.dynamicMaxExpiryMinutes = v; }
    public int getRefundWindowDays()                     { return refundWindowDays; }
    public void setRefundWindowDays(int v)               { this.refundWindowDays = v; }
}
