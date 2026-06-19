package com.example.switching.iso.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import com.example.switching.iso.dto.Pacs002ParseResult;

@Component
public class Pacs002Parser {

    public Pacs002ParseResult parse(String xml) {
        try {
            Document document = parseDocument(xml);

            String messageId = text(document, "MsgId");
            String originalMessageId = text(document, "OrgnlMsgId");
            String originalEndToEndId = text(document, "OrgnlEndToEndId");
            String originalTransactionId = text(document, "OrgnlTxId");
            String transactionStatus = text(document, "TxSts");
            String reasonCode = text(document, "Cd");
            String reasonMessage = text(document, "AddtlInf");

            if (transactionStatus == null || transactionStatus.isBlank()) {
                throw new IllegalArgumentException("PACS.002 TxSts is required");
            }

            return new Pacs002ParseResult(
                    messageId,
                    originalMessageId,
                    originalEndToEndId,
                    originalTransactionId,
                    transactionStatus,
                    reasonCode,
                    reasonMessage
            );

        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse PACS.002 XML", ex);
        }
    }

    private Document parseDocument(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("PACS.002 XML is empty");
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

        if (value.isBlank()) {
            return null;
        }

        return value;
    }
}