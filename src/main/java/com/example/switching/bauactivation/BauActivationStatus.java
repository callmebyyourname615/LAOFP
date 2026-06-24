package com.example.switching.bauactivation;

import java.time.Instant;
import java.util.Map;

public record BauActivationStatus(String releaseId, Instant hypercareStartedAt, int hypercareDay,
                                  Map<String,Boolean> jobs, boolean allRequiredJobsActive,
                                  String status, Instant evaluatedAt) {}
