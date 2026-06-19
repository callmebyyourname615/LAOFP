package com.example.switching.settlement.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SettlementRoutingService {

    private final BigDecimal rtgsThresholdLak;

    public SettlementRoutingService(
            @Value("${switching.settlement.rtgs-threshold-lak}") BigDecimal rtgsThresholdLak) {
        this.rtgsThresholdLak = rtgsThresholdLak;
    }

    public SettlementRoute route(BigDecimal amount, String currency) {
        if ("LAK".equalsIgnoreCase(currency)
                && amount != null
                && amount.compareTo(rtgsThresholdLak) > 0) {
            return new SettlementRoute("RTGS", true);
        }
        return new SettlementRoute("DNS", false);
    }

    public BigDecimal rtgsThresholdLak() {
        return rtgsThresholdLak;
    }

    public record SettlementRoute(String settlementMethod, boolean highValue) {}
}
