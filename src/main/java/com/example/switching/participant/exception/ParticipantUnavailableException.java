package com.example.switching.participant.exception;

public class ParticipantUnavailableException extends RuntimeException {

    public ParticipantUnavailableException(String bankCode, String status) {
        super("Participant is not ACTIVE. bankCode=" + bankCode + ", status=" + status);
    }
}