package com.example.switching.iso.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.switching.iso.dto.Pacs002ParseResult;
import com.example.switching.iso.parser.Pacs002Parser;
import com.example.switching.transfer.entity.TransferEntity;

class LmpsPacsXmlBuilderTest {

    private final LmpsMessageSupport lmps = new LmpsMessageSupport();

    @Test
    void pacs008UsesLmpsEnvelopeAndMessageDefinition() {
        TransferEntity transfer = new TransferEntity();
        transfer.setTransferRef("TRX-20260701000100-ABCDEF12");
        transfer.setSourceBank("SUNDAYBANK");
        transfer.setDestinationBank("PETERBANK");
        transfer.setDebtorAccount("090100000001");
        transfer.setCreditorAccount("090200000001");
        transfer.setDestinationAccountName("MOCK RECEIVER ACCOUNT");
        transfer.setAmount(new BigDecimal("150000.00"));
        transfer.setCurrency("LAK");
        transfer.setReference("LMPS PACS008 test");

        String xml = new Pacs008XmlBuilder(lmps).build(
                transfer,
                "MSG-TRX-20260701000100-ABCDEF12",
                "E2E-TRX-20260701000100-ABCDEF12");

        assertTrue(xml.contains("<Message xmlns=\"urn:apac\""));
        assertTrue(xml.contains("<AppHdr>"));
        assertTrue(xml.contains("<MsgDefIdr>pacs.008.002.08</MsgDefIdr>"));
        assertTrue(xml.contains("<CreditTransfer>"));
        assertTrue(xml.contains("<FIToFICstmrCdtTrf>"));
        assertTrue(xml.contains("<TtlIntrBkSttlmAmt Ccy=\"LAK\">150000.00</TtlIntrBkSttlmAmt>"));
        assertTrue(xml.contains("<MmbId>SUNDAYBANK</MmbId>"));
        assertTrue(xml.contains("<MmbId>PETERBANK</MmbId>"));
    }

    @Test
    void pacs002UsesLmpsEnvelopeAndStillParses() {
        String xml = new Pacs002XmlBuilder(lmps).buildAcceptedResponse(
                "MSG-ORIGINAL",
                "E2E-ORIGINAL",
                "TRX-20260701000100-ABCDEF12",
                "SUNDAYBANK",
                "PETERBANK");

        assertTrue(xml.contains("<Message xmlns=\"urn:apac\""));
        assertTrue(xml.contains("<MsgDefIdr>pacs.002.002.10</MsgDefIdr>"));
        assertTrue(xml.contains("<PaymentStatusReport>"));
        assertTrue(xml.contains("<OrgnlMsgNmId>pacs.008.002.08</OrgnlMsgNmId>"));
        assertTrue(xml.contains("<TxSts>ACTC</TxSts>"));

        Pacs002ParseResult parsed = new Pacs002Parser().parse(xml);
        assertEquals("MSG-ORIGINAL", parsed.originalMessageId());
        assertEquals("E2E-ORIGINAL", parsed.originalEndToEndId());
        assertEquals("TRX-20260701000100-ABCDEF12", parsed.originalTransactionId());
        assertTrue(parsed.accepted());
    }
}
