package com.example.switching.iso.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Camt056XmlBuilder.
 *
 * Verifies that the builder produces structurally correct ISO 20022
 * camt.056.001.08 XML with proper element content and XML escaping.
 */
class Camt056XmlBuilderTest {

    private final Camt056XmlBuilder builder = new Camt056XmlBuilder();

    // ── Namespace and root structure ─────────────────────────────────────────

    @Test
    void output_containsCamt056Namespace() {
        String xml = buildFull();
        assertTrue(xml.contains("urn:iso:std:iso:20022:tech:xsd:camt.056.001.08"),
                "Expected camt.056 namespace in output");
    }

    @Test
    void output_containsRootDocumentElement() {
        String xml = buildFull();
        assertTrue(xml.contains("<Document"), "Expected <Document> root element");
        assertTrue(xml.contains("</Document>"), "Expected </Document> closing tag");
    }

    @Test
    void output_containsFiToFiPaymentCancellationRequest() {
        String xml = buildFull();
        assertTrue(xml.contains("<FIToFIPmtCxlReq>"), "Expected FIToFIPmtCxlReq element");
        assertTrue(xml.contains("</FIToFIPmtCxlReq>"), "Expected closing FIToFIPmtCxlReq element");
    }

    // ── Assgnmt block ────────────────────────────────────────────────────────

    @Test
    void output_containsCancellationMsgIdInAssgnmt() {
        String xml = buildFull();
        // MsgId inside Assgnmt should equal cancellationMsgId
        assertTrue(xml.contains("<MsgId>CXLMSG-001</MsgId>"),
                "Expected cancellationMsgId in Assgnmt/MsgId");
    }

    @Test
    void output_containsCreDtTm() {
        String xml = buildFull();
        assertTrue(xml.contains("<CreDtTm>"), "Expected CreDtTm element");
    }

    // ── Underlying transaction block ─────────────────────────────────────────

    @Test
    void output_containsOriginalMsgId() {
        String xml = buildFull();
        assertTrue(xml.contains("<OrgnlMsgId>ORIG-MSG-001</OrgnlMsgId>"),
                "Expected original message ID");
    }

    @Test
    void output_containsPacs008MsgNmId() {
        String xml = buildFull();
        assertTrue(xml.contains("pacs.008.001.08"),
                "Expected original message name ID referencing pacs.008");
    }

    @Test
    void output_containsOriginalEndToEndId() {
        String xml = buildFull();
        assertTrue(xml.contains("<OrgnlEndToEndId>E2E-TX-001</OrgnlEndToEndId>"),
                "Expected original end-to-end ID");
    }

    @Test
    void output_containsOriginalTxId() {
        String xml = buildFull();
        assertTrue(xml.contains("<OrgnlTxId>TX-REF-001</OrgnlTxId>"),
                "Expected original transaction ID");
    }

    // ── Amount ───────────────────────────────────────────────────────────────

    @Test
    void output_containsAmountAndCurrency() {
        String xml = buildFull();
        assertTrue(xml.contains("Ccy=\"THB\""), "Expected THB currency attribute");
        assertTrue(xml.contains("1500.75"),     "Expected plain amount value");
    }

    @Test
    void output_omitsAmountElementWhenAmountIsNull() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                null, null, "FOCR", null);
        assertFalse(xml.contains("OrgnlIntrBkSttlmAmt"),
                "Amount element must not appear when amount is null");
    }

    @Test
    void output_omitsAmountElementWhenCurrencyIsNull() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.TEN, null, "FOCR", null);
        assertFalse(xml.contains("OrgnlIntrBkSttlmAmt"),
                "Amount element must not appear when currency is null");
    }

    // ── Cancellation reason ───────────────────────────────────────────────────

    @Test
    void output_containsCancellationReasonCode() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "DUPL", null);
        assertTrue(xml.contains("<Cd>DUPL</Cd>"), "Expected DUPL reason code");
    }

    @Test
    void output_usesFocrAsDefaultWhenReasonCodeIsNull() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", null, null);
        assertTrue(xml.contains("<Cd>FOCR</Cd>"), "Expected default FOCR reason code");
    }

    @Test
    void output_usesFocrAsDefaultWhenReasonCodeIsBlank() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "   ", null);
        assertTrue(xml.contains("<Cd>FOCR</Cd>"), "Expected FOCR fallback for blank reason");
    }

    // ── Additional info ───────────────────────────────────────────────────────

    @Test
    void output_containsAdditionalInfoWhenProvided() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "CUST", "Customer requested cancellation");
        assertTrue(xml.contains("<AddtlInf>Customer requested cancellation</AddtlInf>"),
                "Expected additional info element");
    }

    @Test
    void output_omitsAdditionalInfoWhenNull() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "FOCR", null);
        assertFalse(xml.contains("<AddtlInf>"), "AddtlInf must not appear when null");
    }

    @Test
    void output_omitsAdditionalInfoWhenBlank() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "FOCR", "   ");
        assertFalse(xml.contains("<AddtlInf>"), "AddtlInf must not appear when blank");
    }

    // ── XML escaping ──────────────────────────────────────────────────────────

    @Test
    void output_escapesAmpersandInMsgId() {
        String xml = builder.build("CXL&001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "FOCR", null);
        assertTrue(xml.contains("CXL&amp;001"), "Ampersand must be escaped");
        assertFalse(xml.contains("CXL&001"),    "Raw ampersand must not appear in value");
    }

    @Test
    void output_escapesLessThanInAdditionalInfo() {
        String xml = builder.build("CXL-001", "ORIG-001", "E2E-001", "TX-001",
                BigDecimal.valueOf(100), "THB", "CUST", "Amount<0");
        assertTrue(xml.contains("Amount&lt;0"), "Less-than must be escaped in AddtlInf");
    }

    @Test
    void output_escapesQuoteInCancellationId() {
        String xml = builder.build("CXL\"001", "ORIG-001", "E2E-001", "TX-001",
                null, null, "FOCR", null);
        assertTrue(xml.contains("CXL&quot;001"), "Double-quote must be escaped");
    }

    // ── Null end-to-end ID ────────────────────────────────────────────────────

    @Test
    void output_handlesNullEndToEndId() {
        String xml = builder.build("CXL-001", "ORIG-001", null, "TX-001",
                BigDecimal.valueOf(100), "THB", "FOCR", null);
        // Element should still be present, just empty
        assertTrue(xml.contains("<OrgnlEndToEndId>"), "OrgnlEndToEndId element must be present");
        assertTrue(xml.contains("</OrgnlEndToEndId>"), "OrgnlEndToEndId must have closing tag");
    }

    // ── CxlId in TxInf ───────────────────────────────────────────────────────

    @Test
    void output_containsCxlIdMatchingCancellationMsgId() {
        String xml = buildFull();
        assertTrue(xml.contains("<CxlId>CXLMSG-001</CxlId>"),
                "CxlId in TxInf should equal cancellationMsgId");
    }

    // ── Well-formedness quick check ───────────────────────────────────────────

    @Test
    void output_startsWithXmlDeclaration() {
        String xml = buildFull();
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "Output should start with XML declaration");
    }

    // ── Fixture helper ────────────────────────────────────────────────────────

    private String buildFull() {
        return builder.build(
                "CXLMSG-001",
                "ORIG-MSG-001",
                "E2E-TX-001",
                "TX-REF-001",
                new BigDecimal("1500.75"),
                "THB",
                "AM09",
                "Incorrect amount detected by switching centre"
        );
    }
}
