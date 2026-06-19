package com.example.switching.aml.sanctions.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Creates StAX readers with DTD and external entity resolution disabled. */
final class SecureXml {

    private static final byte[] DOCTYPE = "<!DOCTYPE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ENTITY = "<!ENTITY".getBytes(StandardCharsets.US_ASCII);

    private SecureXml() {
    }

    static XMLStreamReader reader(byte[] payload) throws XMLStreamException {
        if (payload == null) {
            throw new XMLStreamException("XML payload must not be null");
        }
        // Some StAX implementations silently skip a prohibited DTD instead of raising an error.
        // Rejecting declarations before parser creation makes XXE behavior deterministic.
        if (containsAsciiIgnoreCase(payload, DOCTYPE) || containsAsciiIgnoreCase(payload, ENTITY)) {
            throw new XMLStreamException("DTD and entity declarations are forbidden");
        }

        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        XMLResolver rejectingResolver = (publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML entities are forbidden");
        };
        factory.setXMLResolver(rejectingResolver);
        return factory.createXMLStreamReader(new ByteArrayInputStream(payload));
    }

    private static boolean containsAsciiIgnoreCase(byte[] haystack, byte[] needle) {
        if (haystack.length < needle.length) {
            return false;
        }
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            boolean match = true;
            for (int offset = 0; offset < needle.length; offset++) {
                int actual = haystack[start + offset] & 0xff;
                int expected = needle[offset] & 0xff;
                if (toUpperAscii(actual) != toUpperAscii(expected)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static int toUpperAscii(int value) {
        return value >= 'a' && value <= 'z' ? value - ('a' - 'A') : value;
    }

    private static void set(XMLInputFactory factory, String property, Object value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException unsupported) {
            // The rejecting resolver and declaration scan remain active when an optional
            // implementation property is unavailable.
        }
    }
}
