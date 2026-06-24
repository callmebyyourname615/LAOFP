package com.example.switching.bauactivation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class BauActivationServiceTest {
    @Test void requiresAllContinuousAssuranceJobs() {
        var p=new BauActivationProperties(); p.setEnabled(true); p.setReleaseId("rc-1");
        p.setHypercareStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        var jobs=new LinkedHashMap<String,Boolean>();
        for(String name:new String[]{"reconciliation","slo","backupFreshness","secretExpiry","capacity","quotaMonitoring","readinessScorecard"}) jobs.put(name,true);
        p.setJobs(jobs);
        var status=new BauActivationService(p).status();
        assertTrue(status.allRequiredJobsActive()); assertEquals("HYPERCARE_ACTIVE",status.status());
    }
    @Test void reachesExitReviewAfterFourteenDays() {
        var p=new BauActivationProperties(); p.setHypercareStartedAt(Instant.now().minus(15,ChronoUnit.DAYS));
        var jobs=new LinkedHashMap<String,Boolean>();
        for(String name:new String[]{"reconciliation","slo","backupFreshness","secretExpiry","capacity","quotaMonitoring","readinessScorecard"}) jobs.put(name,true);
        p.setJobs(jobs);
        assertEquals("EXIT_REVIEW",new BauActivationService(p).status().status());
    }
}
