package com.example.switching.aml.sanctions.model;

/** Type used by the normalized sanctions store. */
public enum SanctionsEntityType {
    PERSON,
    ENTITY;

    public static SanctionsEntityType fromExternal(String value) {
        if (value == null || value.isBlank()) {
            return PERSON;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.contains("ENTITY")
                || normalized.contains("ORGANIZATION")
                || normalized.contains("ORGANISATION")
                || normalized.contains("VESSEL")
                || normalized.contains("AIRCRAFT")) {
            return ENTITY;
        }
        return PERSON;
    }
}
