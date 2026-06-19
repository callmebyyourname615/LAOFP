package com.example.switching.aml.sanctions.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.example.switching.aml.sanctions.model.SanctionsEntityType;
import com.example.switching.aml.sanctions.provider.SanctionsProviderException;

class OfacXmlSanctionsParserTest {

    private final OfacXmlSanctionsParser parser = new OfacXmlSanctionsParser();

    @Test
    void parsesPrimaryNamesAliasesTypesAndIdentifiers() throws IOException {
        byte[] payload = resource("/sanctions/ofac-sample.xml");
        var entries = parser.parse(payload, "OFAC-TEST");

        assertEquals(2, entries.size());
        assertEquals("OFAC:12345", entries.get(0).providerUid());
        assertEquals("JOHN DOE", entries.get(0).primaryName());
        assertEquals(java.util.List.of("JONATHAN DOE"), entries.get(0).aliases());
        assertEquals(SanctionsEntityType.PERSON, entries.get(0).entityType());
        assertEquals(SanctionsEntityType.ENTITY, entries.get(1).entityType());
    }

    @Test
    void rejectsExternalEntityPayload() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE x [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <sdnList><sdnEntry><uid>1</uid><lastName>&xxe;</lastName></sdnEntry></sdnList>
                """;
        assertThrows(SanctionsProviderException.class,
                () -> parser.parse(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "XXE"));
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource " + path);
            return input.readAllBytes();
        }
    }
}
