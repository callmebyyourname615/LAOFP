package com.example.switching.iso.inquiry;

import com.example.switching.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5 — ISO path: inquiry_status_history population.
 *
 * Verifies that {@code inquiry_status_history} is written for the ISO inquiry
 * flow (ACMT.023 / PACS.008), not just the JSON path:
 *
 *  TC-ISH-001  ACMT.023 accepted → ELIGIBLE row in inquiry_status_history
 *  TC-ISH-002  ACMT.023 rejected (inactive source bank) → REJECTED row in inquiry_status_history
 *  TC-ISH-003  PACS.008 succeeds → USED row added to inquiry_status_history
 */
class IsoInquiryStatusHistoryIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK      = "BANK_ISH_A";
    private static final String DEST_BANK        = "BANK_ISH_B";
    private static final String CREDITOR_ACCOUNT = "030300000001";
    private static final BigDecimal AMOUNT       = new BigDecimal("100000.00");
    private static final String CURRENCY         = "LAK";

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate           jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        seedFixtures();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-ISH-001  ACMT.023 accepted → ELIGIBLE in inquiry_status_history
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void acmt023Accepted_writesEligibleStatusHistory() throws Exception {
        String suffix     = "ISH-" + SEQ.incrementAndGet();
        String inquiryRef = sendAcmt023AndGetInquiryRef(suffix);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, reason_code FROM inquiry_status_history WHERE inquiry_ref = ?",
                inquiryRef);

        assertFalse(rows.isEmpty(), "At least one inquiry_status_history row expected for ELIGIBLE ISO inquiry");
        assertEquals("ELIGIBLE", rows.get(0).get("status"),
                "First status row must be ELIGIBLE for an accepted ACMT.023");
        assertNull(rows.get(0).get("reason_code"),
                "reason_code must be null for an ELIGIBLE inquiry");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-ISH-002  ACMT.023 rejected → REJECTED in inquiry_status_history
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void acmt023Rejected_writesRejectedStatusHistory() throws Exception {
        // Suspend source bank so the inquiry is immediately rejected
        jdbcTemplate.update(
                "UPDATE participants SET status = 'SUSPENDED' WHERE bank_code = ?", SOURCE_BANK);

        String suffix    = "ISH-REJ-" + SEQ.incrementAndGet();
        String messageId = "ACMT023-ISH-" + suffix;  // matches acmt023Xml(suffix)

        MvcResult result = mockMvc.perform(post("/api/iso20022/acmt023")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(acmt023Xml(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();
        assertEquals("NMTC", xmlValue(responseXml, "Vrfctn"),
                "Suspended source bank must produce NMTC (rejected) ACMT.024 response");

        // Restore bank for other tests
        jdbcTemplate.update(
                "UPDATE participants SET status = 'ACTIVE' WHERE bank_code = ?", SOURCE_BANK);

        /*
         * The ACMT.024 rejected response does NOT embed InquiryRef in the XML
         * (the response builder omits it for rejections — only accepted responses carry it).
         * Look up the inquiry ref directly from iso_inquiries by message_id.
         */
        String inquiryRef = jdbcTemplate.query(
                """
                SELECT inquiry_ref FROM inquiries
                WHERE channel_id = 'ISO20022_XML'
                  AND message_id = ?
                ORDER BY business_date DESC, id DESC LIMIT 1
                """,
                rs -> rs.next() ? rs.getString("inquiry_ref") : null,
                messageId
        );
        assertNotNull(inquiryRef,
                "inquiries must have a REJECTED row for messageId=" + messageId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, reason_code FROM inquiry_status_history WHERE inquiry_ref = ?",
                inquiryRef);

        assertFalse(rows.isEmpty(), "inquiry_status_history must have a row for the rejected ISO inquiry");
        assertEquals("REJECTED", rows.get(0).get("status"),
                "Status must be REJECTED for inactive source bank");
        assertEquals("BANK_INACTIVE", rows.get(0).get("reason_code"),
                "reason_code must be BANK_INACTIVE for suspended participant");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-ISH-003  PACS.008 accepted → USED row added to inquiry_status_history
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void pacs008AcceptedWithValidInquiry_writesUsedStatusHistory() throws Exception {
        String suffix     = "ISH-USED-" + SEQ.incrementAndGet();
        String inquiryRef = sendAcmt023AndGetInquiryRef(suffix);

        // Verify ELIGIBLE history exists
        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(
                "SELECT status FROM inquiry_status_history WHERE inquiry_ref = ? ORDER BY id ASC",
                inquiryRef);
        assertFalse(beforeRows.isEmpty(), "inquiry_status_history must have ELIGIBLE row before PACS.008");

        // Send PACS.008 using the inquiry ref
        MvcResult pacs008Result = mockMvc.perform(post("/api/iso20022/pacs008")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(pacs008Xml(suffix, inquiryRef)))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = pacs008Result.getResponse().getContentAsString();
        // PACS.002 response must be ACTC (Accepted Technical Validation — the MOCK connector's success code)
        String txSts = xmlValue(responseXml, "TxSts");
        assertTrue("ACTC".equals(txSts) || "ACCP".equals(txSts),
                "PACS.008 with valid inquiry should be ACTC or ACCP. Response: " + responseXml);

        // Now inquiry_status_history must have ELIGIBLE + USED rows
        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(
                """
                SELECT status FROM inquiry_status_history
                WHERE inquiry_ref = ?
                ORDER BY id ASC
                """,
                inquiryRef);

        assertTrue(afterRows.size() >= 2,
                "inquiry_status_history must have at least 2 rows (ELIGIBLE + USED) after PACS.008");

        boolean hasUsed = afterRows.stream()
                .anyMatch(row -> "USED".equals(row.get("status")));
        assertTrue(hasUsed,
                "inquiry_status_history must contain a USED row after PACS.008 completes");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String sendAcmt023AndGetInquiryRef(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/iso20022/acmt023")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(acmt023Xml(suffix)))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();
        assertEquals("MTCH", xmlValue(responseXml, "Vrfctn"),
                "ACMT.023 must be accepted. Response: " + responseXml);

        String inquiryRef = xmlValue(responseXml, "InquiryRef");
        assertNotNull(inquiryRef);
        assertFalse(inquiryRef.isBlank());
        return inquiryRef;
    }

    private String acmt023Xml(String suffix) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.03">
                  <IdVrfctnReq>
                    <Assgnmt>
                      <MsgId>ACMT023-ISH-%s</MsgId>
                      <CreDtTm>2026-05-18T10:00:00Z</CreDtTm>
                    </Assgnmt>
                    <Vrfctn>
                      <Id>VERIFY-ISH-%s</Id>
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
                      <Ustrd>ISH status history test %s</Ustrd>
                    </RmtInf>
                  </IdVrfctnReq>
                </Document>
                """.formatted(suffix, suffix, CREDITOR_ACCOUNT, SOURCE_BANK, DEST_BANK, suffix);
    }

    private String pacs008Xml(String suffix, String inquiryRef) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>PACS008-ISH-%s</MsgId>
                      <CreDtTm>2026-05-18T10:00:00Z</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>INST-ISH-%s</InstrId>
                        <EndToEndId>E2E-ISH-%s</EndToEndId>
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
                            <Id>010100000001</Id>
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
                        <Ustrd>ISH PACS008 status history test %s</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                    <SplmtryData>
                      <PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm>
                      <Envlp>
                        <InquiryRef>%s</InquiryRef>
                      </Envlp>
                    </SplmtryData>
                  </FIToFICstmrCdtTrf>
                </Document>
                """.formatted(
                suffix, suffix, suffix,
                CURRENCY, AMOUNT,
                SOURCE_BANK, DEST_BANK,
                CREDITOR_ACCOUNT,
                suffix,
                inquiryRef
        );
    }

    private void seedFixtures() {
        LocalDateTime now = LocalDateTime.now();

        for (String bank : List.of(SOURCE_BANK, DEST_BANK)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO participants (
                        bank_code, bank_name, status, participant_type,
                        country, currency, created_at, updated_at
                    ) VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', ?, ?)
                    ON CONFLICT (bank_code) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                    """,
                    bank, bank + " (ISH test)", now, now);
        }

        // Connector for DEST_BANK
        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name, bank_code, connector_type, endpoint_url,
                    timeout_ms, enabled, force_reject, reject_reason_code,
                    reject_reason_message, created_at, updated_at
                ) VALUES ('MOCK_ISH_CONNECTOR', ?, 'MOCK', NULL, 5000, TRUE, FALSE,
                          'AC01', 'Mock reject', ?, ?)
                ON CONFLICT (connector_name) DO UPDATE SET enabled = TRUE, force_reject = FALSE,
                    updated_at = EXCLUDED.updated_at
                """,
                DEST_BANK, now, now);

        // Routing rule
        jdbcTemplate.update(
                """
                DELETE FROM routing_rules
                WHERE source_bank = ? AND destination_bank = ? AND message_type = 'PACS_008'
                """,
                SOURCE_BANK, DEST_BANK);

        jdbcTemplate.update(
                """
                INSERT INTO routing_rules (
                    route_code, source_bank, destination_bank, message_type,
                    connector_name, priority, enabled, created_at, updated_at
                ) VALUES ('ROUTE_ISH_TEST', ?, ?, 'PACS_008', 'MOCK_ISH_CONNECTOR', 1, TRUE, ?, ?)
                """,
                SOURCE_BANK, DEST_BANK, now, now);
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

        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        String value = XPathFactory.newInstance()
                .newXPath()
                .evaluate("string(//*[local-name()='" + localName + "'])", doc);

        return value == null ? null : value.trim();
    }
}
