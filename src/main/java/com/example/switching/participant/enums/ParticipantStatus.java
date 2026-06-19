package com.example.switching.participant.enums;

public enum ParticipantStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE,
    DISABLED,
    /** FPRE: PSP suspended from receiving inbound transfers after ≥3 auto-reversals within 30 min. */
    INBOUND_SUSPENDED
}