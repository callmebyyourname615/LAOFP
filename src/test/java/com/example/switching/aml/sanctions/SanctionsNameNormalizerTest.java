package com.example.switching.aml.sanctions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SanctionsNameNormalizerTest {

    @Test
    void removesPunctuationDiacriticsAndRepeatedWhitespace() {
        assertEquals("jose alvarez", SanctionsNameNormalizer.normalize("  José,  Álvarez  "));
    }

    @Test
    void preservesLaoLetters() {
        assertEquals("ທ້າວ ທົດສອບ", SanctionsNameNormalizer.normalize("ທ້າວ ທົດສອບ"));
    }
}
