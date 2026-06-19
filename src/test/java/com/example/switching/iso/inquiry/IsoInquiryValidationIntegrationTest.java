package com.example.switching.iso.inquiry;

import com.example.switching.AbstractIntegrationTest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;

class IsoInquiryValidationIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK = "BANK_A";
    private static final String DESTINATION_BANK = "BANK_B";
    private static final String OTHER_BANK = "BANK_C";

    private static final String DEBTOR_ACCOUNT = "010100000001";
    private static final String CREDITOR_ACCOUNT = "020200000001";
    private static final String WRONG_CREDITOR_ACCOUNT = "020200009999";

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("150000.00");
    private static final String CURRENCY = "LAK";
    private static final String ISO_CHANNEL_ID = "ISO20022_XML";

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(2000);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();

        seedIsoRoutingFixtures();
    }

    @Test
    void acmt023WithWrongXBankCodeIsRejected() throws Exception {
        String suffix = uniqueSuffix();

        MvcResult result = mockMvc.perform(post("/api/iso20022/acmt023")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", OTHER_BANK)
                        .content(acmt023Xml(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();

        assertEquals("NMTC", xmlValue(responseXml, "Vrfctn"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("X-Bank-Code must match inquiry source bank"),
                () -> "Expected ACMT.023 X-Bank-Code mismatch to be rejected. Response XML: " + responseXml
        );
    }

    @Test
    void pacs008WithUnknownInquiryRefIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        String unknownInquiryRef = "INQ-UNKNOWN-" + suffix;

        String responseXml = postPacs008(pacs008Xml(
                suffix,
                unknownInquiryRef,
                SOURCE_BANK,
                DESTINATION_BANK,
                CREDITOR_ACCOUNT
        ), SOURCE_BANK);

        assertEquals("RJCT", xmlValue(responseXml, "TxSts"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("InquiryRef not found"),
                () -> "Expected unknown InquiryRef to be rejected. Response XML: " + responseXml
        );
    }

    @Test
    void pacs008SourceBankMismatchIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = createInquiryAndReturnInquiryRef(suffix);

        String responseXml = postPacs008(pacs008Xml(
                uniqueSuffix(),
                inquiryRef,
                OTHER_BANK,
                DESTINATION_BANK,
                CREDITOR_ACCOUNT
        ), OTHER_BANK);

        assertEquals("RJCT", xmlValue(responseXml, "TxSts"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("Inquiry sourceBank does not match PACS.008"),
                () -> "Expected source bank mismatch to be rejected. Response XML: " + responseXml
        );
    }

    @Test
    void pacs008DestinationBankMismatchIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = createInquiryAndReturnInquiryRef(suffix);

        String responseXml = postPacs008(pacs008Xml(
                uniqueSuffix(),
                inquiryRef,
                SOURCE_BANK,
                OTHER_BANK,
                CREDITOR_ACCOUNT
        ), SOURCE_BANK);

        assertEquals("RJCT", xmlValue(responseXml, "TxSts"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("Inquiry destinationBank does not match PACS.008"),
                () -> "Expected destination bank mismatch to be rejected. Response XML: " + responseXml
        );
    }

    @Test
    void pacs008CreditorAccountMismatchIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = createInquiryAndReturnInquiryRef(suffix);

        String responseXml = postPacs008(pacs008Xml(
                uniqueSuffix(),
                inquiryRef,
                SOURCE_BANK,
                DESTINATION_BANK,
                WRONG_CREDITOR_ACCOUNT
        ), SOURCE_BANK);

        assertEquals("RJCT", xmlValue(responseXml, "TxSts"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("Inquiry creditorAccount does not match PACS.008"),
                () -> "Expected creditor account mismatch to be rejected. Response XML: " + responseXml
        );
    }

    @Test
    void pacs008WithUsedInquiryRefIsRejectedForNewInstruction() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = createInquiryAndReturnInquiryRef(suffix);

        String firstResponseXml = postPacs008(pacs008Xml(
                suffix,
                inquiryRef,
                SOURCE_BANK,
                DESTINATION_BANK,
                CREDITOR_ACCOUNT
        ), SOURCE_BANK);

        assertEquals(
                "ACTC",
                xmlValue(firstResponseXml, "TxSts"),
                () -> "Expected first PACS.008 with valid InquiryRef to be accepted. Response XML: " + firstResponseXml
        );

        String secondResponseXml = postPacs008(pacs008Xml(
                uniqueSuffix(),
                inquiryRef,
                SOURCE_BANK,
                DESTINATION_BANK,
                CREDITOR_ACCOUNT
        ), SOURCE_BANK);

        assertEquals("RJCT", xmlValue(secondResponseXml, "TxSts"));
        assertTrue(
                xmlValue(secondResponseXml, "AddtlInf").contains("status=USED"),
                () -> "Expected new PACS.008 with used InquiryRef to be rejected. Response XML: " + secondResponseXml
        );
    }

    private String createInquiryAndReturnInquiryRef(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/iso20022/acmt023")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(acmt023Xml(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();

        assertEquals("MTCH", xmlValue(responseXml, "Vrfctn"));

        String inquiryRef = xmlValue(responseXml, "InquiryRef");
        assertNotNull(inquiryRef);
        assertFalse(inquiryRef.isBlank());
        assertTrue(inquiryRef.startsWith("INQ-"));

        return inquiryRef;
    }

    private String postPacs008(String pacs008Xml, String xBankCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/iso20022/pacs008")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", xBankCode)
                        .content(pacs008Xml))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getContentAsString();
    }

    private void seedIsoRoutingFixtures() {
        LocalDateTime now = LocalDateTime.now();

        upsertParticipant(SOURCE_BANK, "Source Test Bank", now);
        upsertParticipant(DESTINATION_BANK, "Receiver Test Bank", now);
        upsertParticipant(OTHER_BANK, "Other Test Bank", now);

        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name,
                    bank_code,
                    connector_type,
                    endpoint_url,
                    timeout_ms,
                    enabled,
                    force_reject,
                    reject_reason_code,
                    reject_reason_message,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'MOCK', NULL, 5000, TRUE, FALSE, 'AC01', 'Mock Bank rejected transfer', ?, ?)
                ON CONFLICT (connector_name) DO UPDATE SET
                    bank_code = EXCLUDED.bank_code,
                    connector_type = EXCLUDED.connector_type,
                    enabled = TRUE,
                    force_reject = FALSE,
                    reject_reason_code = EXCLUDED.reject_reason_code,
                    reject_reason_message = EXCLUDED.reject_reason_message,
                    updated_at = EXCLUDED.updated_at
                """,
                "MOCK_BANK_B_CONNECTOR",
                DESTINATION_BANK,
                now,
                now
        );

        jdbcTemplate.update(
                """
                DELETE FROM routing_rules
                WHERE source_bank = ?
                  AND destination_bank = ?
                  AND message_type = 'PACS_008'
                """,
                SOURCE_BANK,
                DESTINATION_BANK
        );

        jdbcTemplate.update(
                """
                INSERT INTO routing_rules (
                    route_code,
                    source_bank,
                    destination_bank,
                    message_type,
                    connector_name,
                    priority,
                    enabled,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'PACS_008', ?, 1, TRUE, ?, ?)
                """,
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                SOURCE_BANK,
                DESTINATION_BANK,
                "MOCK_BANK_B_CONNECTOR",
                now,
                now
        );
    }

    private void upsertParticipant(String bankCode, String bankName, LocalDateTime now) {
        jdbcTemplate.update(
                """
                INSERT INTO participants (
                    bank_code,
                    bank_name,
                    status,
                    participant_type,
                    country,
                    currency,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                ON CONFLICT (bank_code) DO UPDATE SET
                    bank_name = EXCLUDED.bank_name,
                    status = 'ACTIVE',
                    participant_type = 'DIRECT',
                    country = 'LA',
                    currency = 'LAK',
                    updated_at = EXCLUDED.updated_at
                """,
                bankCode,
                bankName,
                now,
                now
        );
    }

    private String acmt023Xml(String suffix) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.03">
                  <IdVrfctnReq>
                    <Assgnmt>
                      <MsgId>ACMT023-BANKA-ISO-%s</MsgId>
                      <CreDtTm>2026-05-12T03:00:00Z</CreDtTm>
                    </Assgnmt>
                    <Vrfctn>
                      <Id>VERIFY-%s</Id>
                      <PtyAndAcctId>
                        <Acct>
                          <Id>
                            <Othr>
                              <Id>%s</Id>
                            </Othr>
                          </Id>
                        </Acct>
                      </PtyAndAcctId>
                    </Vrfctn>
                    <DbtrAgt>
                      <FinInstnId>
                        <BICFI>%s</BICFI>
                      </FinInstnId>
                    </DbtrAgt>
                    <CdtrAgt>
                      <FinInstnId>
                        <BICFI>%s</BICFI>
                      </FinInstnId>
                    </CdtrAgt>
                    <RmtInf>
                      <Ustrd>ISO-INQ-5A negative integration test %s</Ustrd>
                    </RmtInf>
                  </IdVrfctnReq>
                </Document>
                """.formatted(
                suffix,
                suffix,
                CREDITOR_ACCOUNT,
                SOURCE_BANK,
                DESTINATION_BANK,
                suffix
        );
    }

    private String pacs008Xml(
            String suffix,
            String inquiryRef,
            String debtorAgentBic,
            String creditorAgentBic,
            String creditorAccount
    ) {
        String supplementaryData = "";
        if (inquiryRef != null && !inquiryRef.isBlank()) {
            supplementaryData = """
                    <SplmtryData>
                      <PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm>
                      <Envlp>
                        <InquiryRef>%s</InquiryRef>
                      </Envlp>
                    </SplmtryData>
                    """.formatted(inquiryRef);
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>MSG-BANKA-ISO-%s</MsgId>
                      <CreDtTm>2026-05-12T03:00:00Z</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>INST-BANKA-ISO-%s</InstrId>
                        <EndToEndId>E2E-BANKA-ISO-%s</EndToEndId>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                      <DbtrAgt>
                        <FinInstnId>
                          <BICFI>%s</BICFI>
                        </FinInstnId>
                      </DbtrAgt>
                      <CdtrAgt>
                        <FinInstnId>
                          <BICFI>%s</BICFI>
                        </FinInstnId>
                      </CdtrAgt>
                      <DbtrAcct>
                        <Id>
                          <Othr>
                            <Id>%s</Id>
                          </Othr>
                        </Id>
                      </DbtrAcct>
                      <CdtrAcct>
                        <Id>
                          <Othr>
                            <Id>%s</Id>
                          </Othr>
                        </Id>
                      </CdtrAcct>
                      <RmtInf>
                        <Ustrd>PACS008 negative integration test %s</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                    %s
                  </FIToFICstmrCdtTrf>
                </Document>
                """.formatted(
                suffix,
                suffix,
                suffix,
                CURRENCY,
                TRANSFER_AMOUNT,
                debtorAgentBic,
                creditorAgentBic,
                DEBTOR_ACCOUNT,
                creditorAccount,
                suffix,
                supplementaryData
        );
    }

    private String xmlValue(String xml, String localName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Document document = factory
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        String value = XPathFactory.newInstance()
                .newXPath()
                .evaluate("string(//*[local-name()='" + localName + "'])", document);

        return value == null ? null : value.trim();
    }

    private String uniqueSuffix() {
        int sequence = SUFFIX_SEQUENCE.incrementAndGet();
        long millisPart = Math.floorMod(System.currentTimeMillis(), 10_000L);
        return "N" + millisPart + sequence;
    }
}
