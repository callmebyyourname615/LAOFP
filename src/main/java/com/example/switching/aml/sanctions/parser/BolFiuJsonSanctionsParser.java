package com.example.switching.aml.sanctions.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.switching.aml.sanctions.model.SanctionsEntityType;
import com.example.switching.aml.sanctions.model.SanctionsEntry;
import com.example.switching.aml.sanctions.provider.SanctionsProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Defensive BoL/FIU JSON adapter. It accepts an array root or an array under
 * entries/data/items/results and recognizes common field aliases. Exact mapping can be
 * tightened when the official BoL contract is received without changing import logic.
 */
@Component
public class BolFiuJsonSanctionsParser {

    private final ObjectMapper objectMapper;

    public BolFiuJsonSanctionsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SanctionsEntry> parse(byte[] payload, String sourceReference) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode array = locateArray(root);
            List<SanctionsEntry> entries = new ArrayList<>();
            for (JsonNode item : array) {
                String uid = firstText(item, "id", "uid", "referenceNumber", "reference_no", "listId");
                String name = firstText(item, "name", "fullName", "full_name", "entityName", "entity_name");
                if (uid.isBlank() || name.isBlank()) {
                    throw new SanctionsProviderException("BoL/FIU entry is missing id or name");
                }
                String type = firstText(item, "type", "entityType", "entity_type", "subjectType");
                List<String> aliases = stringList(firstNode(item, "aliases", "alias", "aka", "alternativeNames"));
                Map<String, Object> identifiers = new LinkedHashMap<>();
                JsonNode identifiersNode = firstNode(item, "identifiers", "identity", "documents", "ids");
                if (identifiersNode != null && !identifiersNode.isNull()) {
                    identifiers.put("providerPayload", objectMapper.convertValue(identifiersNode, Object.class));
                }
                entries.add(new SanctionsEntry(
                        "BOL:" + uid,
                        name,
                        aliases,
                        SanctionsEntityType.fromExternal(type),
                        identifiers,
                        sourceReference));
            }
            return List.copyOf(entries);
        } catch (IOException | IllegalArgumentException error) {
            throw new SanctionsProviderException("Unable to parse BoL/FIU sanctions JSON", error);
        }
    }

    private JsonNode locateArray(JsonNode root) {
        if (root != null && root.isArray()) {
            return root;
        }
        JsonNode candidate = firstNode(root, "entries", "data", "items", "results");
        if (candidate == null || !candidate.isArray()) {
            throw new SanctionsProviderException("BoL/FIU response does not contain a sanctions array");
        }
        return candidate;
    }

    private String firstText(JsonNode node, String... fields) {
        JsonNode value = firstNode(node, fields);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private JsonNode firstNode(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    values.add(item.asText());
                } else if (item.isObject()) {
                    String name = firstText(item, "name", "alias", "value", "fullName");
                    if (!name.isBlank()) {
                        values.add(name);
                    }
                }
            }
        } else if (node.isObject()) {
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                JsonNode value = iterator.next();
                if (value.isTextual()) {
                    values.add(value.asText());
                }
            }
        }
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }
}
