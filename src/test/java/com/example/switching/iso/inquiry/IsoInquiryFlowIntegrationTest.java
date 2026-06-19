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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;

import com.example.switching.outbox.service.OutboxProcessorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class IsoInquiryFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_CHANNEL_ID = "ISO20022_XML";
    private static final String SOURCE_BANK = "BANK_A";
    private static final String DESTINATION_BANK = "BANK_B";
    private static final String DEBTOR_ACCOUNT = "010100000001";
    private static final String CREDITOR_ACCOUNT = "020200000001";
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("150000.00");
    private static final String CURRENCY = "LAK";

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(1000);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxProcessorService outboxProcessorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();

        seedIsoRoutingFixtures();
    }

    @Test
    void getIsoInquiryNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/iso-inquiries/{inquiryRef}", "INQ-NOT-FOUND"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pacs008WithoutInquiryRefIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        String pacs008Xml = pacs008Xml(suffix, null);

        MvcResult result = mockMvc.perform(post("/api/iso20022/pacs008")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(pacs008Xml))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();

        assertEquals("RJCT", xmlValue(responseXml, "TxSts"));
        assertTrue(
                xmlValue(responseXml, "AddtlInf").contains("InquiryRef is required"),
                "PACS.008 without InquiryRef must explain that InquiryRef is required"
        );
    }

    @Test
    void acmt023ThenPacs008FullInquiryTransferFlow() throws Exception {
        String suffix = uniqueSuffix();

        String inquiryRef = createInquiryAndAssertEligible(suffix);

        JsonNode beforeTransfer = getInquiry(inquiryRef);
        assertEquals(inquiryRef, beforeTransfer.path("inquiryRef").asText());
        assertEquals("ELIGIBLE", beforeTransfer.path("status").asText());
        assertTrue(beforeTransfer.path("debtorAccount").isNull());
        assertEquals(CREDITOR_ACCOUNT, beforeTransfer.path("creditorAccount").asText());
        assertTrue(beforeTransfer.path("usedByTransferRef").isNull());

        String pacs008Xml = pacs008Xml(suffix, inquiryRef);
        String firstPacs002 = postPacs008(pacs008Xml);

        assertEquals(
                "ACTC",
                xmlValue(firstPacs002, "TxSts"),
                () -> "Expected valid PACS.008 with InquiryRef to be accepted. Response XML: " + firstPacs002
        );
        String transferRef = xmlValue(firstPacs002, "AcctSvcrRef");
        assertNotNull(transferRef);
        assertFalse(transferRef.isBlank());

        /*
         * ISO idempotency regression:
         * Retrying the exact same PACS.008 must return the same transferRef even after
         * the inquiry has been marked USED by the first accepted request.
         */
        String retryPacs002 = postPacs008(pacs008Xml);
        assertEquals(
                "ACTC",
                xmlValue(retryPacs002, "TxSts"),
                () -> "Expected exact same PACS.008 retry to be idempotently accepted. Response XML: " + retryPacs002
        );
        assertEquals(transferRef, xmlValue(retryPacs002, "AcctSvcrRef"));

        processOutboxAndWaitForSuccess(transferRef);

        JsonNode afterTransfer = getInquiry(inquiryRef);
        assertEquals("USED", afterTransfer.path("status").asText());
        assertTrue(afterTransfer.path("debtorAccount").isNull());
        assertEquals(CREDITOR_ACCOUNT, afterTransfer.path("creditorAccount").asText());
        assertEquals(transferRef, afterTransfer.path("usedByTransferRef").asText());

        JsonNode transfer = getTransfer(transferRef);
        assertEquals(transferRef, transfer.path("transferRef").asText());
        assertEquals("SETTLED", transfer.path("status").asText());
        assertEquals("SETTLED", transfer.path("currentStatus").asText());
        assertEquals(SOURCE_BANK, transfer.path("sourceBank").asText());
        assertEquals(DESTINATION_BANK, transfer.path("destinationBank").asText());
        assertEquals(DEBTOR_ACCOUNT, transfer.path("debtorAccount").asText());
        assertEquals(CREDITOR_ACCOUNT, transfer.path("creditorAccount").asText());
        assertEquals(0, TRANSFER_AMOUNT.compareTo(transfer.path("amount").decimalValue()));
        assertEquals(CURRENCY, transfer.path("currency").asText());
        assertEquals(inquiryRef, transfer.path("inquiryRef").asText());

        String usedInquiryPacs002 = postPacs008(pacs008Xml(uniqueSuffix(), inquiryRef));
        assertEquals("RJCT", xmlValue(usedInquiryPacs002, "TxSts"));
        assertTrue(
                xmlValue(usedInquiryPacs002, "AddtlInf").contains("status=USED"),
                "New PACS.008 with already-used InquiryRef must be rejected"
        );
    }

    private String createInquiryAndAssertEligible(String suffix) throws Exception {
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

    private JsonNode getInquiry(String inquiryRef) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/iso-inquiries/{inquiryRef}", inquiryRef)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getTransfer(String transferRef) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transfers/{transferRef}", transferRef)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String postPacs008(String pacs008Xml) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/iso20022/pacs008")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(pacs008Xml))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getContentAsString();
    }

    private void processOutboxAndWaitForSuccess(String transferRef) throws Exception {
        Long outboxEventId = jdbcTemplate.query(
                """
                SELECT id
                FROM outbox_messages
                WHERE transaction_ref = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getLong("id") : null,
                transferRef
        );

        assertNotNull(outboxEventId, "PACS.008 acceptance must enqueue an outbox event");

        outboxProcessorService.processSingleEvent(outboxEventId);

        long deadline = System.currentTimeMillis() + 5_000L;
        String status = transferStatus(transferRef);

        while (!"SETTLED".equals(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200L);
            status = transferStatus(transferRef);
        }

        assertEquals("SETTLED", status, "Outbox dispatch should mark transfer SETTLED");
    }

    private String transferStatus(String transferRef) {
        return jdbcTemplate.query(
                """
                SELECT status
                FROM transactions
                WHERE transaction_ref = ?
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString("status") : null,
                transferRef
        );
    }

    private void seedIsoRoutingFixtures() {
        LocalDateTime now = LocalDateTime.now();

        upsertParticipant(SOURCE_BANK, "Source Test Bank", now);
        upsertParticipant(DESTINATION_BANK, "Receiver Test Bank", now);

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
                      <Ustrd>ISO-INQ-4D automated integration test %s</Ustrd>
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

    private String pacs008Xml(String suffix, String inquiryRef) {
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
                        <Ustrd>PACS008 automated integration test %s</Ustrd>
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
                SOURCE_BANK,
                DESTINATION_BANK,
                DEBTOR_ACCOUNT,
                CREDITOR_ACCOUNT,
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
        /*
         * Keep IDs short because transfer.idempotency_key is VARCHAR(100).
         *
         * The production idempotency key is derived from channel/source/message identifiers.
         * A long test suffix can make valid PACS.008 requests fail with:
         * "Data too long for column 'idempotency_key'".
         *
         * Format example: T12345678
         */
        int sequence = SUFFIX_SEQUENCE.incrementAndGet();
        long millisPart = Math.floorMod(System.currentTimeMillis(), 10_000L);
        return "T" + millisPart + sequence;
    }
}
