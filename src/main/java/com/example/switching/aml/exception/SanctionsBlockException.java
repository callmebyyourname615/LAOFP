package com.example.switching.aml.exception;

/**
 * Thrown when a transaction party matches a sanctions list entry.
 * Maps to LFP-SANCTIONS-001 (HTTP 422 Unprocessable Entity).
 */
public class SanctionsBlockException extends RuntimeException {

    private final String matchedEntity;
    private final String listType;

    public SanctionsBlockException(String matchedEntity, String listType) {
        super("Transaction blocked: sanctions match on '" + matchedEntity + "' in " + listType + " list");
        this.matchedEntity = matchedEntity;
        this.listType = listType;
    }

    public String getMatchedEntity() { return matchedEntity; }
    public String getListType() { return listType; }
}
