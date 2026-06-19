package com.example.switching.aml.sanctions.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

import com.example.switching.aml.sanctions.model.SanctionsEntityType;
import com.example.switching.aml.sanctions.model.SanctionsEntry;
import com.example.switching.aml.sanctions.provider.SanctionsProviderException;

/** Parser for OFAC legacy SDN.XML. Namespace changes do not affect local-name parsing. */
@Component
public class OfacXmlSanctionsParser {

    public List<SanctionsEntry> parse(byte[] payload, String sourceReference) {
        List<SanctionsEntry> entries = new ArrayList<>();
        try {
            XMLStreamReader reader = SecureXml.reader(payload);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && "sdnEntry".equalsIgnoreCase(reader.getLocalName())) {
                    entries.add(parseEntry(reader, sourceReference));
                }
            }
            reader.close();
            return List.copyOf(entries);
        } catch (XMLStreamException | IllegalArgumentException error) {
            throw new SanctionsProviderException("Unable to parse OFAC SDN XML safely", error);
        }
    }

    private SanctionsEntry parseEntry(XMLStreamReader reader, String sourceReference)
            throws XMLStreamException {
        String uid = null;
        String firstName = "";
        String lastName = "";
        String type = "";
        List<String> aliases = new ArrayList<>();
        List<String> programs = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = reader.getLocalName();
                if (depth == 2 && "uid".equalsIgnoreCase(local)) {
                    uid = readText(reader);
                    depth--;
                } else if (depth == 2 && "firstName".equalsIgnoreCase(local)) {
                    firstName = readText(reader);
                    depth--;
                } else if (depth == 2 && "lastName".equalsIgnoreCase(local)) {
                    lastName = readText(reader);
                    depth--;
                } else if (depth == 2 && "sdnType".equalsIgnoreCase(local)) {
                    type = readText(reader);
                    depth--;
                } else if ("program".equalsIgnoreCase(local)) {
                    programs.add(readText(reader));
                    depth--;
                } else if ("aka".equalsIgnoreCase(local)) {
                    aliases.add(parseAka(reader));
                    depth--;
                } else if ("id".equalsIgnoreCase(local)) {
                    String id = parseId(reader);
                    if (!id.isBlank()) {
                        ids.add(id);
                    }
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        String name = joinName(firstName, lastName);
        if (name.isBlank()) {
            throw new SanctionsProviderException("OFAC entry " + uid + " has no name");
        }
        Map<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("programs", programs.stream().filter(v -> !v.isBlank()).distinct().toList());
        identifiers.put("documents", ids);
        return new SanctionsEntry(
                "OFAC:" + uid,
                name,
                aliases,
                SanctionsEntityType.fromExternal(type),
                identifiers,
                sourceReference);
    }

    private String parseAka(XMLStreamReader reader) throws XMLStreamException {
        String first = "";
        String last = "";
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if ("firstName".equalsIgnoreCase(reader.getLocalName())) {
                    first = readText(reader);
                    depth--;
                } else if ("lastName".equalsIgnoreCase(reader.getLocalName())) {
                    last = readText(reader);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return joinName(first, last);
    }

    private String parseId(XMLStreamReader reader) throws XMLStreamException {
        String type = "";
        String number = "";
        String country = "";
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = reader.getLocalName();
                if ("idType".equalsIgnoreCase(local)) {
                    type = readText(reader);
                    depth--;
                } else if ("idNumber".equalsIgnoreCase(local)) {
                    number = readText(reader);
                    depth--;
                } else if ("idCountry".equalsIgnoreCase(local)) {
                    country = readText(reader);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return String.join("|", List.of(type, number, country)).replaceAll("\\|+$", "");
    }

    private String readText(XMLStreamReader reader) throws XMLStreamException {
        String value = reader.getElementText();
        return value == null ? "" : value.trim();
    }

    private String joinName(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
