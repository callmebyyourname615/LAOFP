package com.example.switching.billpayment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "switching.billpayment")
public class BillPaymentProperties {

    /** Timeout for biller API calls (seconds). Default 30. */
    private int billerApiTimeoutSeconds = 30;

    /** Bill token TTL (minutes). Default 10. */
    private int tokenTtlMinutes = 10;

    /** 24-hour duplicate-payment block window (hours). Default 24. */
    private int duplicateWindowHours = 24;

    public int getBillerApiTimeoutSeconds() { return billerApiTimeoutSeconds; }
    public void setBillerApiTimeoutSeconds(int v) { this.billerApiTimeoutSeconds = v; }

    public int getTokenTtlMinutes() { return tokenTtlMinutes; }
    public void setTokenTtlMinutes(int v) { this.tokenTtlMinutes = v; }

    public int getDuplicateWindowHours() { return duplicateWindowHours; }
    public void setDuplicateWindowHours(int v) { this.duplicateWindowHours = v; }
}
