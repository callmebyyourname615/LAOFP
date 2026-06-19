package com.example.switching.aml.sanctions;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Produces a stable screening key without changing the original display name.
 * Diacritics, punctuation and repeated whitespace are removed. Unicode letters/digits
 * are retained so Lao and other non-Latin names continue to be searchable.
 */
public final class SanctionsNameNormalizer {

    private SanctionsNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "");
        return decomposed
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
