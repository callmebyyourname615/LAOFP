package com.example.switching.continuousassurance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.continuous-assurance")
public class ContinuousAssuranceProperties {
    private boolean enabled = false;
    private double availabilitySlo = 99.95;
    private double paymentSuccessSlo = 99.99;
    private double p95LatencyMaxMs = 500;
    private double reconciliationDelayMaxMinutes = 5;
    private double greenScore = 90;
    private double amberScore = 75;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getAvailabilitySlo() { return availabilitySlo; }
    public void setAvailabilitySlo(double v) { availabilitySlo = v; }
    public double getPaymentSuccessSlo() { return paymentSuccessSlo; }
    public void setPaymentSuccessSlo(double v) { paymentSuccessSlo = v; }
    public double getP95LatencyMaxMs() { return p95LatencyMaxMs; }
    public void setP95LatencyMaxMs(double v) { p95LatencyMaxMs = v; }
    public double getReconciliationDelayMaxMinutes() { return reconciliationDelayMaxMinutes; }
    public void setReconciliationDelayMaxMinutes(double v) { reconciliationDelayMaxMinutes = v; }
    public double getGreenScore() { return greenScore; }
    public void setGreenScore(double v) { greenScore = v; }
    public double getAmberScore() { return amberScore; }
    public void setAmberScore(double v) { amberScore = v; }
}
