package com.example.switching.participant.lifecycle;

import java.time.Instant;
import java.util.UUID;

public record ParticipantLifecycleDecision(UUID caseId, String participantCode, String status, Instant effectiveAt) {}
