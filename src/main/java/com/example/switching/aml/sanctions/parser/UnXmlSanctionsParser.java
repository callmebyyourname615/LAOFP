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

/** Parser for the UN Security Council consolidated legacy XML feed. */
@Component
public class UnXmlSanctionsParser {

    public List<SanctionsEntry> parse(byte[] payload, String sourceReference) {
        List<SanctionsEntry> entries = new ArrayList<>();
        try {
            XMLStreamReader reader = SecureXml.reader(payload);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("INDIVIDUAL".equalsIgnoreCase(local)) {
                        entries.add(parseRecord(reader, SanctionsEntityType.PERSON, sourceReference));
                    } else if ("ENTITY".equalsIgnoreCase(local)) {
                        entries.add(parseRecord(reader, SanctionsEntityType.ENTITY, sourceReference));
                    }
                }
            }
            reader.close();
            return List.copyOf(entries);
        } catch (XMLStreamException | IllegalArgumentException error) {
            throw new SanctionsProviderException("Unable to parse UN consolidated XML safely", error);
        }
    }

    private SanctionsEntry parseRecord(XMLStreamReader reader,
                                       SanctionsEntityType entityType,
                                       String sourceReference) throws XMLStreamException {
        String dataId = "";
        String referenceNumber = "";
        String first = "";
        String second = "";
        String third = "";
        String fourth = "";
        String entityName = "";
        List<String> aliases = new ArrayList<>();
        List<String> nationalities = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<String> birthDates = new ArrayList<>();
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = reader.getLocalName().toUpperCase();
                switch (local) {
                    case "DATAID" -> {
                        dataId = readText(reader);
                        depth--;
                    }
                    case "REFERENCE_NUMBER" -> {
                        referenceNumber = readText(reader);
                        depth--;
                    }
                    case "FIRST_NAME" -> {
                        first = readText(reader);
                        depth--;
                    }
                    case "SECOND_NAME" -> {
                        second = readText(reader);
                        depth--;
                    }
                    case "THIRD_NAME" -> {
                        third = readText(reader);
                        depth--;
                    }
                    case "FOURTH_NAME" -> {
                        fourth = readText(reader);
                        depth--;
                    }
                    case "NAME" -> {
                        if (entityType == SanctionsEntityType.ENTITY && depth == 2) {
                            entityName = readText(reader);
                            depth--;
                        }
                    }
                    case "INDIVIDUAL_ALIAS", "ENTITY_ALIAS" -> {
                        String alias = parseAlias(reader);
                        if (!alias.isBlank()) {
                            aliases.add(alias);
                        }
                        depth--;
                    }
                    case "INDIVIDUAL_DOCUMENT" -> {
                        String document = parseDocument(reader);
                        if (!document.isBlank()) {
                            documents.add(document);
                        }
                        depth--;
                    }
                    case "DATE_OF_BIRTH" -> {
                        String birthDate = readText(reader);
                        if (!birthDate.isBlank()) {
                            birthDates.add(birthDate);
                        }
                        depth--;
                    }
                    case "NATIONALITY" -> {
                        String nationality = parseNestedValue(reader, "VALUE");
                        if (!nationality.isBlank()) {
                            nationalities.add(nationality);
                        }
                        depth--;
                    }
                    default -> {
                        // Unknown elements are intentionally ignored while preserving depth.
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        String composedName = joinName(first, second, third, fourth);
        String primaryName = entityType == SanctionsEntityType.ENTITY && !entityName.isBlank()
                ? entityName
                : composedName;
        String uid = !referenceNumber.isBlank() ? referenceNumber : dataId;
        if (uid.isBlank() || primaryName.isBlank()) {
            throw new SanctionsProviderException("UN record is missing DATAID/reference/name");
        }

        Map<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("dataId", dataId);
        identifiers.put("referenceNumber", referenceNumber);
        identifiers.put("documents", documents);
        identifiers.put("dateOfBirth", birthDates);
        identifiers.put("nationalities", nationalities);
        return new SanctionsEntry(
                "UN:" + uid,
                primaryName,
                aliases,
                entityType,
                identifiers,
                sourceReference);
    }

    private String parseAlias(XMLStreamReader reader) throws XMLStreamException {
        String aliasName = "";
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if ("ALIAS_NAME".equalsIgnoreCase(reader.getLocalName())) {
                    aliasName = readText(reader);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return aliasName;
    }

    private String parseDocument(XMLStreamReader reader) throws XMLStreamException {
        String type = "";
        String number = "";
        String country = "";
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = reader.getLocalName().toUpperCase();
                if ("TYPE_OF_DOCUMENT".equals(local)) {
                    type = readText(reader);
                    depth--;
                } else if ("NUMBER".equals(local)) {
                    number = readText(reader);
                    depth--;
                } else if ("ISSUING_COUNTRY".equals(local)) {
                    country = readText(reader);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return String.join("|", List.of(type, number, country)).replaceAll("\\|+$", "");
    }

    private String parseNestedValue(XMLStreamReader reader, String element) throws XMLStreamException {
        String value = "";
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if (element.equalsIgnoreCase(reader.getLocalName())) {
                    value = readText(reader);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return value;
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
