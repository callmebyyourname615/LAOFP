package com.example.switching.governance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlEvidenceTest {
    @Test void hashesAreDeterministicAndLengthDelimited(){
        assertEquals(ControlEvidence.sha256("ab","c"),ControlEvidence.sha256("ab","c"));
        assertNotEquals(ControlEvidence.sha256("ab","c"),ControlEvidence.sha256("a","bc"));
        assertTrue(ControlEvidence.sha256("x").matches("[0-9a-f]{64}"));
    }
    @Test void rejectsNonSha256(){assertThrows(IllegalArgumentException.class,()->ControlEvidence.requireSha256("abc","hash"));}
}
