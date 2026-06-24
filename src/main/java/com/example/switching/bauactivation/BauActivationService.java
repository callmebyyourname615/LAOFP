package com.example.switching.bauactivation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="switching.phase81.bau.enabled",havingValue="true")
public class BauActivationService {
    private static final List<String> REQUIRED = List.of("reconciliation","slo","backupFreshness","secretExpiry","capacity","quotaMonitoring","readinessScorecard");
    private final BauActivationProperties properties;
    public BauActivationService(BauActivationProperties properties){this.properties=properties;}
    public BauActivationStatus status(){
        Instant now=Instant.now(), start=properties.getHypercareStartedAt();
        int day=start==null?0:(int)Math.max(0,Duration.between(start,now).toDays());
        boolean all=REQUIRED.stream().allMatch(k->Boolean.TRUE.equals(properties.getJobs().get(k)));
        String status=!all?"BLOCKED":day>=14?"EXIT_REVIEW":"HYPERCARE_ACTIVE";
        return new BauActivationStatus(properties.getReleaseId(),start,day,Map.copyOf(properties.getJobs()),all,status,now);
    }
}
