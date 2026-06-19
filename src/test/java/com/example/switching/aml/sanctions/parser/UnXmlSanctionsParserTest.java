package com.example.switching.aml.sanctions.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.example.switching.aml.sanctions.model.SanctionsEntityType;

class UnXmlSanctionsParserTest {

    private final UnXmlSanctionsParser parser = new UnXmlSanctionsParser();

    @Test
    void parsesIndividualsEntitiesAliasesAndReferenceNumbers() throws IOException {
        var entries = parser.parse(resource("/sanctions/un-sample.xml"), "UN-TEST");

        assertEquals(2, entries.size());
        assertEquals("UN:QDi.100", entries.get(0).providerUid());
        assertEquals("AHMAD ALI KHAN", entries.get(0).primaryName());
        assertEquals(java.util.List.of("AHMED A KHAN"), entries.get(0).aliases());
        assertEquals(SanctionsEntityType.PERSON, entries.get(0).entityType());
        assertEquals("GLOBAL EXAMPLE FOUNDATION", entries.get(1).primaryName());
        assertEquals(SanctionsEntityType.ENTITY, entries.get(1).entityType());
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource " + path);
            return input.readAllBytes();
        }
    }
}
