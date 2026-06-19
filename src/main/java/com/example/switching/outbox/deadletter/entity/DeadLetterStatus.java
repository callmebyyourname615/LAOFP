package com.example.switching.outbox.deadletter.entity;

public enum DeadLetterStatus {
    QUARANTINED,
    REPLAY_REQUESTED,
    APPROVED,
    REPLAYED,
    DISCARDED
}
