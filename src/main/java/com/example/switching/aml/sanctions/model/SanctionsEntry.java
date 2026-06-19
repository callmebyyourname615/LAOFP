package com.example.switching.aml.sanctions.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral sanctions entry ready for validation and staging. */
public record SanctionsEntry(
        String providerUid,
        String primaryName,
        List<String> aliases,
        SanctionsEntityType entityType,
        Map<String, Object> identifiers,
        String sourceReference) {

    public SanctionsEntry {
        providerUid = requireText(providerUid, "providerUid");
        primaryName = requireText(primaryName, "primaryName");
        String canonicalPrimaryName = primaryName;
        aliases = aliases == null
                ? List.of()
                : aliases.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .filter(value -> !value.equalsIgnoreCase(canonicalPrimaryName))
                        .distinct()
                        .toList();
        entityType = entityType == null ? SanctionsEntityType.PERSON : entityType;
        identifiers = identifiers == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(identifiers));
        sourceReference = sourceReference == null ? "" : sourceReference.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
