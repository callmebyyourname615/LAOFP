package com.example.switching.aml.sanctions.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BolFiuJsonSanctionsParserTest {

    @Test
    void acceptsDefensiveBolContractAliases() throws IOException {
        BolFiuJsonSanctionsParser parser = new BolFiuJsonSanctionsParser(new ObjectMapper());
        var entries = parser.parse(resource("/sanctions/bol-sample.json"), "BOL-TEST");

        assertEquals(1, entries.size());
        assertEquals("BOL:BOL-001", entries.getFirst().providerUid());
        assertEquals("ທ້າວ ທົດສອບ", entries.getFirst().primaryName());
        assertEquals(java.util.List.of("THAO THOTSOP"), entries.getFirst().aliases());
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource " + path);
            return input.readAllBytes();
        }
    }
}
