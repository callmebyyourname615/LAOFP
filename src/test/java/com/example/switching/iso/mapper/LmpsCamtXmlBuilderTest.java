package com.example.switching.iso.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.switching.iso.dto.Camt006ParseResult;
import com.example.switching.iso.parser.Camt006Parser;
import com.example.switching.outbox.dto.StatusEnquiryResult;

class LmpsCamtXmlBuilderTest {

    private final LmpsMessageSupport lmps = new LmpsMessageSupport();

    @Test
    void camt005UsesLmpsEnvelopeAndGetTransactionPayload() {
        String xml = new Camt005XmlBuilder(lmps).build(
                "TRX-20260701000100-ABCDEF12",
                "MSG-TRX-20260701000100-ABCDEF12",
                "E2E-TRX-20260701000100-ABCDEF12",
                "SUNDAYBANK",
                "PETERBANK",
                new BigDecimal("150000.00"),
                "LAK",
                "090100000001",
                "090200000001");

        assertTrue(xml.contains("<Message xmlns=\"urn:apac\""));
        assertTrue(xml.contains("<MsgDefIdr>camt.005.001.08</MsgDefIdr>"));
        assertTrue(xml.contains("<GetTran>"));
        assertTrue(xml.contains("<GetTx>"));
        assertTrue(xml.contains("<MsgId>MSG-TRX-20260701000100-ABCDEF12</MsgId>"));
        assertTrue(xml.contains("<EndToEndId>E2E-TRX-20260701000100-ABCDEF12</EndToEndId>"));
        assertTrue(xml.contains("<EQAmt>150000.00</EQAmt>"));
        assertTrue(xml.contains("<Ccy>LAK</Ccy>"));
    }

    @Test
    void camt006AcceptedUsesLmpsEnvelopeAndParses() {
        String xml = new Camt006XmlBuilder(lmps).build(
                "TRX-20260701000100-ABCDEF12",
                "E2E-TRX-20260701000100-ABCDEF12",
                "SUNDAYBANK",
                "PETERBANK",
                new BigDecimal("150000.00"),
                "LAK",
                StatusEnquiryResult.accepted("MOCK-12345678", "Destination credited"));

        assertTrue(xml.contains("<Message xmlns=\"urn:apac\""));
        assertTrue(xml.contains("<MsgDefIdr>camt.006.001.08</MsgDefIdr>"));
        assertTrue(xml.contains("<RtrTran>"));
        assertTrue(xml.contains("<Prtry>ACTC</Prtry>"));

        Camt006ParseResult parsed = new Camt006Parser().parse(xml);
        assertEquals("TRX-20260701000100-ABCDEF12", parsed.transactionId());
        assertEquals("E2E-TRX-20260701000100-ABCDEF12", parsed.endToEndId());
        assertEquals("0000", parsed.statusCode());
        assertEquals("ACTC", parsed.reasonCode());
        assertTrue(parsed.accepted());
    }

    @Test
    void camt006NotFoundParsesBusinessError() {
        String xml = new Camt006XmlBuilder(lmps).build(
                "TRX-20260701000100-ABCDEF12",
                "E2E-TRX-20260701000100-ABCDEF12",
                "SUNDAYBANK",
                "PETERBANK",
                new BigDecimal("150000.00"),
                "LAK",
                new StatusEnquiryResult(
                        StatusEnquiryResult.Status.NOT_FOUND,
                        null,
                        "PE01",
                        "Payment not found"));

        Camt006ParseResult parsed = new Camt006Parser().parse(xml);
        assertEquals("PE01", parsed.errorCode());
        assertEquals("Payment not found", parsed.errorDescription());
        assertTrue(parsed.notFound());
    }
}
