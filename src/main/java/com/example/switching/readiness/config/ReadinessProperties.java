package com.example.switching.readiness.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.readiness")
public class ReadinessProperties {
    private boolean enabled = false;
    private Duration evidenceMaxAge = Duration.ofHours(24);
    private Set<String> requiredControls = new LinkedHashSet<>(Set.of(
            "BUILD-MAVEN-VERIFY", "REPOSITORY-VERIFY", "PERF-10K", "PERF-BURST-20K",
            "SETTLEMENT-500K", "BACKUP-RESTORE", "PITR", "DR", "FINANCIAL-INTEGRITY",
            "SECRET-ROTATION", "SMOS-SECURITY", "ALERT-LIFECYCLE"));
    private Set<String> requiredApprovalRoles = new LinkedHashSet<>(Set.of(
            "ENGINEERING_LEAD", "QA_LEAD", "SRE_LEAD", "SECOPS", "CHANGE_MANAGER", "BUSINESS_OWNER"));
    private int minimumUniqueApprovers = 4;
    private Set<String> nonWaivableCategories = new LinkedHashSet<>(Set.of(
            "TRANSACTION_LOSS", "LEDGER_IMBALANCE", "SECRET_LEAK", "UNSIGNED_RELEASE", "BACKUP_RESTORE_FAILURE"));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getEvidenceMaxAge() { return evidenceMaxAge; }
    public void setEvidenceMaxAge(Duration evidenceMaxAge) { this.evidenceMaxAge = evidenceMaxAge; }
    public Set<String> getRequiredControls() { return requiredControls; }
    public void setRequiredControls(Set<String> requiredControls) { this.requiredControls = requiredControls; }
    public Set<String> getRequiredApprovalRoles() { return requiredApprovalRoles; }
    public void setRequiredApprovalRoles(Set<String> requiredApprovalRoles) { this.requiredApprovalRoles = requiredApprovalRoles; }
    public int getMinimumUniqueApprovers() { return minimumUniqueApprovers; }
    public void setMinimumUniqueApprovers(int minimumUniqueApprovers) { this.minimumUniqueApprovers = minimumUniqueApprovers; }
    public Set<String> getNonWaivableCategories() { return nonWaivableCategories; }
    public void setNonWaivableCategories(Set<String> nonWaivableCategories) { this.nonWaivableCategories = nonWaivableCategories; }
}
