package com.example.switching.participant.exception;

/**
 * Thrown when an OAuth token is validated for a PSP whose {@code oauth_clients}
 * record has {@code status = SUSPENDED}.
 *
 * <p>Maps to {@code LFP-2004} (HTTP 403 FORBIDDEN) in the global error catalog.
 */
public class ParticipantSuspendedException extends RuntimeException {

    public ParticipantSuspendedException(String pspId) {
        super("PSP participant is suspended: " + pspId);
    }
}
