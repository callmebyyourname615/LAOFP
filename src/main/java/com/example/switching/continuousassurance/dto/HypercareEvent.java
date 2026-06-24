package com.example.switching.continuousassurance.dto;

import java.time.Instant;

public record HypercareEvent(String eventId, int day, String type, String summary, String owner, Instant occurredAt) {
}
