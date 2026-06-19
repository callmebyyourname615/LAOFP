package com.example.switching.iso.inquiry;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;

@Component
public class Acmt023XmlParser {

    public Acmt023InquiryRequest parse(String xml) {
        if (!StringUtils.hasText(xml)) {
            throw new IllegalArgumentException("ACMT.023 XML body is required");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            Acmt023InquiryRequest request = new Acmt023InquiryRequest();

            request.setMessageId(required(text(document, "MsgId"), "MsgId"));
            request.setInstructionId(firstNonBlank(text(document, "InstrId"), text(document, "Id")));
            request.setEndToEndId(firstNonBlank(text(document, "EndToEndId"), text(document, "EndToEndID")));

            request.setSourceBank(required(nthText(document, "BICFI", 0), "source BICFI"));
            request.setDestinationBank(required(nthText(document, "BICFI", 1), "destination BICFI"));

            /*
             * Current local ACMT.023 profile verifies the destination / creditor account.
             *
             * Parse the leaf account Id directly. Do not read parent nodes such as:
             * - <PtyAndAcctId>
             * - <Acct>
             * - parent <Id>
             *
             * Reading parent nodes can return raw XML whitespace around the account number.
             */
            String verifiedAccount = firstNonBlank(
                    xpathText(
                            document,
                            "//*[local-name()='Vrfctn']" +
                                    "/*[local-name()='PtyAndAcctId']" +
                                    "/*[local-name()='Acct']" +
                                    "/*[local-name()='Id']" +
                                    "/*[local-name()='Othr']" +
                                    "/*[local-name()='Id']/text()"
                    ),
                    xpathText(
                            document,
                            "//*[local-name()='Vrfctn']" +
                                    "/*[local-name()='PtyAndAcctId']" +
                                    "/*[local-name()='Acct']" +
                                    "/*[local-name()='Id']" +
                                    "/*[local-name()='IBAN']/text()"
                    ),
                    lastText(document, "Id")
            );

            request.setDebtorAccount(null);
            request.setCreditorAccount(required(verifiedAccount, "creditor account"));

            String amountText = firstNonBlank(text(document, "Amt"), text(document, "InstdAmt"), text(document, "IntrBkSttlmAmt"));
            if (StringUtils.hasText(amountText)) {
                request.setAmount(new BigDecimal(amountText.trim()));
            }

            String currency = firstNonBlank(attr(document, "Amt", "Ccy"), attr(document, "InstdAmt", "Ccy"), attr(document, "IntrBkSttlmAmt", "Ccy"));
            request.setCurrency(currency);

            request.setReference(firstNonBlank(text(document, "Ustrd"), "ISO inquiry account verification"));

            return request;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ACMT.023 XML: " + e.getMessage(), e);
        }
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String text(Document document, String localName) {
        var nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private String nthText(Document document, String localName, int index) {
        var nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes == null || nodes.getLength() <= index) {
            return null;
        }
        return nodes.item(index).getTextContent();
    }

    private String lastText(Document document, String localName) {
        var nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(nodes.getLength() - 1).getTextContent();
    }

    private String attr(Document document, String localName, String attrName) {
        var nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }

        var attr = nodes.item(0).getAttributes().getNamedItem(attrName);
        if (attr == null) {
            return null;
        }

        return attr.getTextContent();
    }

    private String xpathText(Document document, String expression) throws Exception {
        String value = XPathFactory.newInstance()
                .newXPath()
                .evaluate(expression, document);

        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
