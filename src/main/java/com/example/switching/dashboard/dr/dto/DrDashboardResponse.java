package com.example.switching.dashboard.dr.dto;

import java.time.Instant;

public record DrDashboardResponse(Instant generatedAt, boolean replicaInRecovery, Double replicaLagSeconds,
                                  Instant lastBackupAt, Instant lastRestoreAt, Instant lastDrillAt,
                                  Integer observedRpoMinutes, Integer observedRtoMinutes,
                                  boolean offsiteCopyVerified, String status) {}
