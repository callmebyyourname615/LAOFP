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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5 — ISO inquiry concurrent idempotency.
 *
 * Verifies that two threads firing ACMT.023 simultaneously with the same MsgId
 * do not create two inquiries rows.  The race loser must catch the UNIQUE
 * constraint violation on (channel_id, message_id) and return the winner's
 * existing inquiryRef — not a 500.
 *
 * TC-CI-001  Sequential retry — same MsgId returns same inquiryRef
 * TC-CI-002  Concurrent race — both threads get MTCH with identical inquiryRef,
 *            exactly 1 row in inquiries
 */
class IsoInquiryConcurrentIdempotencyIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK      = "BANK_CI_A";
    private static final String DEST_BANK        = "BANK_CI_B";
    private static final String CREDITOR_ACCOUNT = "040400000001";

    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate           jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        seedFixtures();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CI-001  Sequential retry — same MsgId returns the same inquiryRef
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void sequentialRetry_sameMsgId_returnsSameInquiryRef() throws Exception {
        String msgId = "ACMT023-CI-SEQ-" + SEQ.incrementAndGet();

        String first  = sendAcmt023(msgId);
        String second = sendAcmt023(msgId);

        assertNotNull(first,  "First call must return inquiryRef");
        assertNotNull(second, "Second call must return inquiryRef");
        assertEquals(first, second, "Retry with same MsgId must return existing inquiryRef");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inquiries WHERE channel_id = 'ISO20022_XML' AND message_id = ?",
                Integer.class, msgId);
        assertEquals(1, count, "Exactly 1 inquiries row must exist for the message_id");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-CI-002  Concurrent race — both threads MTCH, 1 row in inquiries
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void concurrentSameMsgId_bothGetMtch_exactlyOneRowCreated() throws Exception {
        String msgId = "ACMT023-CI-CONC-" + SEQ.incrementAndGet();
        String xml   = acmt023Xml(msgId);

        CountDownLatch startGun = new CountDownLatch(1);
        AtomicReference<String>    ref1 = new AtomicReference<>();
        AtomicReference<String>    ref2 = new AtomicReference<>();
        AtomicReference<Throwable> err1 = new AtomicReference<>();
        AtomicReference<Throwable> err2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                startGun.await();
                ref1.set(sendAcmt023WithXml(xml));
            } catch (Throwable t) { err1.set(t); }
        });

        Thread t2 = new Thread(() -> {
            try {
                startGun.await();
                ref2.set(sendAcmt023WithXml(xml));
            } catch (Throwable t) { err2.set(t); }
        });

        t1.start();
        t2.start();
        startGun.countDown();   // release both threads simultaneously
        t1.join(10_000);
        t2.join(10_000);

        assertNull(err1.get(), "Thread 1 must not throw: " + err1.get());
        assertNull(err2.get(), "Thread 2 must not throw: " + err2.get());

        assertNotNull(ref1.get(), "Thread 1 must receive an inquiryRef");
        assertNotNull(ref2.get(), "Thread 2 must receive an inquiryRef");

        assertEquals(ref1.get(), ref2.get(),
                "Both concurrent threads must get the same inquiryRef — race loser must return winner's ref");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inquiries WHERE channel_id = 'ISO20022_XML' AND message_id = ?",
                Integer.class, msgId);
        assertEquals(1, count,
                "Exactly 1 inquiries row must exist even when two threads raced on the same MsgId");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String sendAcmt023(String msgId) throws Exception {
        return sendAcmt023WithXml(acmt023Xml(msgId));
    }

    private String sendAcmt023WithXml(String xml) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/iso20022/acmt023")
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Bank-Code", SOURCE_BANK)
                        .content(xml))
                .andExpect(status().isOk())
                .andReturn();

        String responseXml = result.getResponse().getContentAsString();
        assertEquals("MTCH", xmlValue(responseXml, "Vrfctn"),
                "ACMT.023 must be accepted (MTCH). Response: " + responseXml);

        return xmlValue(responseXml, "InquiryRef");
    }

    private String acmt023Xml(String msgId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.03">
                  <IdVrfctnReq>
                    <Assgnmt>
                      <MsgId>%s</MsgId>
                      <CreDtTm>2026-05-18T12:00:00Z</CreDtTm>
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
                  </IdVrfctnReq>
                </Document>
                """.formatted(msgId, msgId, CREDITOR_ACCOUNT, SOURCE_BANK, DEST_BANK);
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
                    bank, bank + " (CI test)", now, now);
        }
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

        return value == null || value.isBlank() ? null : value.trim();
    }
}
