package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.switching.settlement.service.SettlementRoutingService;

class SettlementRoutingServiceTest {

    private final SettlementRoutingService service =
            new SettlementRoutingService(new BigDecimal("500000000"));

    @Test
    void route_lakAboveThresholdUsesRtgs() {
        var route = service.route(new BigDecimal("600000000.00"), "LAK");

        assertEquals("RTGS", route.settlementMethod());
        assertTrue(route.highValue());
    }

    @Test
    void route_lakAtThresholdStillUsesDns() {
        var route = service.route(new BigDecimal("500000000.00"), "LAK");

        assertEquals("DNS", route.settlementMethod());
        assertFalse(route.highValue());
    }

    @Test
    void route_nonLakUsesDns() {
        var route = service.route(new BigDecimal("600000000.00"), "USD");

        assertEquals("DNS", route.settlementMethod());
        assertFalse(route.highValue());
    }
}
