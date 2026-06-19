package com.example.switching.participant.exception;

public class ParticipantAlreadyExistsException extends RuntimeException {

    public ParticipantAlreadyExistsException(String bankCode) {
        super("Participant already exists: " + bankCode);
    }
}
