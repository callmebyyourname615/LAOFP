package com.example.switching.crossborder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "switching.crossborder")
public class CrossBorderProperties {

    /** How long a binding FX quote is valid (seconds). Default 30. */
    private int quoteTtlSeconds = 30;

    /** Amount threshold above which purposeCode + sourceOfFunds are mandatory. Default 5 M LAK. */
    private long purposeCodeThresholdLak = 5_000_000L;

    /** Adapter URLs and identifiers. */
    private String promptpayUrl = "http://mock-promptpay:9099/promptpay";
    private String cnapsUrl     = "http://mock-cnaps:9099/cnaps";
    private String napasUrl     = "http://mock-napas:9099/napas";
    private String swiftUrl     = "http://mock-swift:9099/swift";
    private String swiftBic     = "BFILLALAXXX";

    /** HTTP connect + read timeout for adapter calls (seconds). Default 30. */
    private int adapterTimeoutSeconds = 30;

    public int    getQuoteTtlSeconds()            { return quoteTtlSeconds; }
    public void   setQuoteTtlSeconds(int v)       { this.quoteTtlSeconds = v; }
    public long   getPurposeCodeThresholdLak()    { return purposeCodeThresholdLak; }
    public void   setPurposeCodeThresholdLak(long v){ this.purposeCodeThresholdLak = v; }
    public String getPromptpayUrl()               { return promptpayUrl; }
    public void   setPromptpayUrl(String v)       { this.promptpayUrl = v; }
    public String getCnapsUrl()                   { return cnapsUrl; }
    public void   setCnapsUrl(String v)           { this.cnapsUrl = v; }
    public String getNapasUrl()                   { return napasUrl; }
    public void   setNapasUrl(String v)           { this.napasUrl = v; }
    public String getSwiftUrl()                   { return swiftUrl; }
    public void   setSwiftUrl(String v)           { this.swiftUrl = v; }
    public String getSwiftBic()                   { return swiftBic; }
    public void   setSwiftBic(String v)           { this.swiftBic = v; }
    public int    getAdapterTimeoutSeconds()      { return adapterTimeoutSeconds; }
    public void   setAdapterTimeoutSeconds(int v) { this.adapterTimeoutSeconds = v; }
}
