package com.example.switching.continuousassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.example.switching.continuousassurance.model.HypercareStatus;
import com.example.switching.continuousassurance.service.HypercareService;

class HypercareServiceTest {
    @Test
    void tracksMandatoryDayMilestones() {
        HypercareService service = new HypercareService();
        service.start(Instant.now().minus(15, ChronoUnit.DAYS));
        service.addEvent(1, "DAY1_RECON", "clean", "OPS");
        service.addEvent(3, "DAY3_SETTLEMENT", "complete", "OPS");
        service.addEvent(7, "DAY7_WEEKLY_RECON", "clean", "OPS");
        service.addEvent(14, "DAY14_EXIT_REVIEW", "approved", "CHANGE_MANAGER");
        assertEquals(HypercareStatus.EXIT_READY, service.summary().status());
        assertEquals(0, service.summary().missingMilestones().size());
        assertEquals(HypercareStatus.COMPLETED, service.complete().status());
        assertThrows(IllegalStateException.class, () -> service.start(Instant.now()));
    }
}
