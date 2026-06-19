package com.example.switching.participant.exception;

public class ParticipantNotFoundException extends RuntimeException {

    public ParticipantNotFoundException(String bankCode) {
        super("Participant not found: " + bankCode);
    }
}