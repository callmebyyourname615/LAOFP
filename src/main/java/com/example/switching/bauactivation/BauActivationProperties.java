package com.example.switching.bauactivation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.phase81.bau")
public class BauActivationProperties {
    private boolean enabled;
    private String releaseId = "unassigned";
    private Instant hypercareStartedAt;
    private Map<String, Boolean> jobs = new LinkedHashMap<>();
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public String getReleaseId(){return releaseId;} public void setReleaseId(String releaseId){this.releaseId=releaseId;}
    public Instant getHypercareStartedAt(){return hypercareStartedAt;} public void setHypercareStartedAt(Instant value){this.hypercareStartedAt=value;}
    public Map<String,Boolean> getJobs(){return jobs;} public void setJobs(Map<String,Boolean> jobs){this.jobs=jobs;}
}
