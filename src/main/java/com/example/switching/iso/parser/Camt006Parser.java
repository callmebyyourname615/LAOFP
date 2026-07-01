package com.example.switching.iso.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import com.example.switching.iso.dto.Camt006ParseResult;

@Component
public class Camt006Parser {

    public Camt006ParseResult parse(String xml) {
        try {
            Document document = parseDocument(xml);

            return new Camt006ParseResult(
                    text(document, "MsgId"),
                    text(document, "TxId"),
                    text(document, "EndToEndId"),
                    firstPrtryAfter(document, "Cd"),
                    firstPrtryAfter(document, "Rsn"),
                    firstPrtryAfter(document, "Err"),
                    text(document, "Desc"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse CAMT.006 XML", ex);
        }
    }

    private Document parseDocument(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("CAMT.006 XML is empty");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String firstPrtryAfter(Document document, String parentLocalName) {
        var parents = document.getElementsByTagNameNS("*", parentLocalName);
        if (parents == null || parents.getLength() == 0) {
            return null;
        }
        for (int i = 0; i < parents.getLength(); i++) {
            var childNodes = parents.item(i).getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                var child = childNodes.item(j);
                if ("Prtry".equals(child.getLocalName()) && child.getTextContent() != null) {
                    String value = child.getTextContent().trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String text(Document document, String tagName) {
        var nodes = document.getElementsByTagNameNS("*", tagName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        var node = nodes.item(0);
        if (node == null || node.getTextContent() == null) {
            return null;
        }
        String value = node.getTextContent().trim();
        return value.isBlank() ? null : value;
    }
}
