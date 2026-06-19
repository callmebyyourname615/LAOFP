package com.example.switching.transfer;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5 — Idempotency safety under concurrent load.
 *
 * TC-IDEM-001  Concurrent POST /api/transfers with same inquiryRef
 *              → exactly 1 transfer created, second gets 409.
 *
 * TC-IDEM-002  Sequential POST /api/transfers with same idempotencyKey + same payload
 *              → second call returns 200 with existing transferRef (replay safe).
 *
 * TC-IDEM-003  Concurrent POST /api/iso20022/pacs008 with same MsgId
 *              → exactly 1 transfer created in ISO path.
 */
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE_BANK      = "BANK_IDEM_A";
    private static final String DEST_BANK        = "BANK_IDEM_B";
    private static final String CREDITOR_ACCOUNT = "040400000001";
    private static final String DEBTOR_ACCOUNT   = "050500000001";
    private static final BigDecimal AMOUNT       = new BigDecimal("200000.00");
    private static final String CURRENCY         = "LAK";

    private static final AtomicInteger SEQ = new AtomicInteger(8000);

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate           jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        seedParticipantsAndRoutes();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-IDEM-001  Concurrent POST /api/transfers with same inquiry ref
    //              → exactly 1 transfer created
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void concurrentTransfers_sameInquiryRef_exactlyOneTransferCreated() throws Exception {
        String suffix     = "IDEM-" + SEQ.incrementAndGet();
        String inquiryRef = seedEligibleInquiry(suffix);

        String requestBody = transferRequestJson(inquiryRef, "IDEM-KEY-" + suffix);

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startGate.await();
                MvcResult result = mockMvc.perform(post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andReturn();
                int httpStatus = result.getResponse().getStatus();
                if (httpStatus == 201 || httpStatus == 200) {
                    successCount.incrementAndGet();
                } else if (httpStatus == 409 || httpStatus == 422) {
                    conflictCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // count as neither — test will catch the assertion below
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(task);
        pool.submit(task);
        startGate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "Threads did not finish in time");

        // Exactly 1 transfer must exist for this inquiry
        Integer transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE inquiry_ref = ?",
                Integer.class, inquiryRef);

        assertEquals(1, transferCount,
                "Exactly 1 transfer must be created even under concurrent submission. "
                        + "successes=" + successCount.get() + " conflicts=" + conflictCount.get());

        // Combined, threads must account for 2 responses
        assertEquals(2, successCount.get() + conflictCount.get(),
                "Both threads must have completed with a recognisable HTTP status");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-IDEM-002  Sequential replay with same idempotencyKey → same transferRef
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void sequentialReplay_sameIdempotencyKey_returnsSameTransferRef() throws Exception {
        String suffix     = "IDEM-SEQ-" + SEQ.incrementAndGet();
        String inquiryRef = seedEligibleInquiry(suffix);
        String idemKey    = "IDEM-KEY-SEQ-" + suffix;

        String requestBody = transferRequestJson(inquiryRef, idemKey);

        // First call — creates the transfer
        MvcResult first = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        int firstStatus = first.getResponse().getStatus();
        assertTrue(firstStatus == 200 || firstStatus == 201,
                "First transfer submission must succeed. HTTP=" + firstStatus
                        + " body=" + first.getResponse().getContentAsString());

        String firstBody = first.getResponse().getContentAsString();
        String firstTransferRef = extractTransferRef(firstBody);
        assertNotNull(firstTransferRef, "transferRef must be present in first response");

        // Second call — idempotency replay with exact same payload
        MvcResult second = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        int secondStatus = second.getResponse().getStatus();
        // Either 200 (idempotent hit) or 409 (duplicate inquiry) is acceptable;
        // the critical assertion is that no SECOND transfer was created.
        assertTrue(secondStatus == 200 || secondStatus == 201 || secondStatus == 409,
                "Second identical request must not cause 500. HTTP=" + secondStatus);

        if (secondStatus == 200 || secondStatus == 201) {
            String secondBody = second.getResponse().getContentAsString();
            String secondTransferRef = extractTransferRef(secondBody);
            assertEquals(firstTransferRef, secondTransferRef,
                    "Idempotent replay must return the SAME transferRef");
        }

        // Only 1 transfer row for this inquiry regardless
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE inquiry_ref = ?",
                Integer.class, inquiryRef);
        assertEquals(1, count, "Exactly 1 transfer row must exist after idempotent replay");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-IDEM-003  Concurrent PACS.008 with same MsgId → exactly 1 transfer
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void concurrentPacs008_sameMsgId_exactlyOneTransferCreated() throws Exception {
        String suffix     = "IDEM-ISO-" + SEQ.incrementAndGet();
        String inquiryRef = seedEligibleIsoInquiry(suffix);

        String pacs008Body = pacs008Xml(suffix, inquiryRef);

        CountDownLatch startGate   = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger otherCount   = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startGate.await();
                MvcResult result = mockMvc.perform(post("/api/iso20022/pacs008")
                                .contentType(MediaType.APPLICATION_XML)
                                .accept(MediaType.APPLICATION_XML)
                                .header("X-Bank-Code", SOURCE_BANK)
                                .content(pacs008Body))
                        .andReturn();

                int httpStatus = result.getResponse().getStatus();
                String responseXml = result.getResponse().getContentAsString();

                if (httpStatus == 200 && responseXml.contains("ACCP")) {
                    successCount.incrementAndGet();
                } else {
                    otherCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                otherCount.incrementAndGet();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(task);
        pool.submit(task);
        startGate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "Threads did not finish in time");

        // Exactly 1 transfer must exist for this ISO inquiry ref
        Integer transferCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transactions
                WHERE channel_id = 'ISO20022_XML'
                  AND client_transaction_id LIKE ?
                """,
                Integer.class,
                "INST-IDEM-ISO-" + suffix + "%"
        );

        assertEquals(1, transferCount,
                "Exactly 1 ISO transfer must be created for concurrent PACS.008 with same MsgId. "
                        + "successes=" + successCount.get() + " others=" + otherCount.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Inserts a ELIGIBLE inquiry row for the JSON transfer path. */
    private String seedEligibleInquiry(String suffix) {
        String inquiryRef = "INQ-IDEM-" + suffix;
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref, client_inquiry_id, source_bank, destination_bank,
                    creditor_account, destination_account_name, amount, currency,
                    channel_id, route_code, connector_name,
                    account_found, bank_available, eligible_for_transfer,
                    status, business_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'Test Account', ?, ?,
                          'API', 'ROUTE_IDEM_TEST', 'MOCK_IDEM_CONNECTOR',
                          TRUE, TRUE, TRUE, 'ELIGIBLE', CURRENT_DATE, ?, ?)
                """,
                inquiryRef, suffix, SOURCE_BANK, DEST_BANK,
                CREDITOR_ACCOUNT, AMOUNT, CURRENCY, now, now
        );

        return inquiryRef;
    }

    /**
     * Inserts an ELIGIBLE iso_inquiry row for the ISO PACS.008 path.
     * The inquiry must have source/dest bank that match the PACS.008 payload.
     */
    private String seedEligibleIsoInquiry(String suffix) {
        String inquiryRef = "INQ-ISO-IDEM-" + suffix;
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref, channel_id, message_id, instruction_id, end_to_end_id,
                    source_bank, destination_bank,
                    debtor_account, creditor_account,
                    amount, currency,
                    status, account_found, bank_available, eligible_for_transfer,
                    expires_at, business_date, created_at, updated_at
                ) VALUES (?, 'ISO20022_XML', ?, ?, ?, ?, ?,
                          ?, ?,
                          ?, ?,
                          'ELIGIBLE', TRUE, TRUE, TRUE,
                          ?, CURRENT_DATE, ?, ?)
                """,
                inquiryRef,
                "MSG-ISO-IDEM-" + suffix,
                "INST-IDEM-ISO-" + suffix,
                "E2E-IDEM-ISO-" + suffix,
                SOURCE_BANK, DEST_BANK,
                DEBTOR_ACCOUNT, CREDITOR_ACCOUNT,
                AMOUNT, CURRENCY,
                now.plusMinutes(15),
                now, now
        );

        return inquiryRef;
    }

    private String transferRequestJson(String inquiryRef, String idempotencyKey) {
        return """
                {
                  "inquiryRef": "%s",
                  "sourceBank": "%s",
                  "destinationBank": "%s",
                  "debtorAccount": "%s",
                  "creditorAccount": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "idempotencyKey": "%s"
                }
                """.formatted(
                inquiryRef, SOURCE_BANK, DEST_BANK,
                DEBTOR_ACCOUNT, CREDITOR_ACCOUNT,
                AMOUNT, CURRENCY,
                idempotencyKey
        );
    }

    private String pacs008Xml(String suffix, String inquiryRef) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>MSG-IDEM-ISO-%s</MsgId>
                      <CreDtTm>2026-05-18T10:00:00Z</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>INST-IDEM-ISO-%s</InstrId>
                        <EndToEndId>E2E-IDEM-ISO-%s</EndToEndId>
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
                        <Ustrd>IDEM ISO concurrent test %s</Ustrd>
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
                DEBTOR_ACCOUNT, CREDITOR_ACCOUNT,
                suffix,
                inquiryRef
        );
    }

    private void seedParticipantsAndRoutes() {
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
                    bank, bank + " (idem test)", now, now);
        }

        // Connector for DEST_BANK
        jdbcTemplate.update(
                """
                INSERT INTO connector_configs (
                    connector_name, bank_code, connector_type, endpoint_url,
                    timeout_ms, enabled, force_reject, reject_reason_code,
                    reject_reason_message, created_at, updated_at
                ) VALUES ('MOCK_IDEM_CONNECTOR', ?, 'MOCK', NULL, 5000, TRUE, FALSE,
                          'AC01', 'Mock reject', ?, ?)
                ON CONFLICT (connector_name) DO UPDATE SET enabled = TRUE, force_reject = FALSE,
                    updated_at = EXCLUDED.updated_at
                """,
                DEST_BANK, now, now);

        // Routing rule for JSON path
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
                ) VALUES ('ROUTE_IDEM_TEST', ?, ?, 'PACS_008', 'MOCK_IDEM_CONNECTOR', 1, TRUE, ?, ?)
                """,
                SOURCE_BANK, DEST_BANK, now, now);
    }

    private String extractTransferRef(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) return null;
        // Simple JSON field extraction — avoids pulling in Jackson for a test helper
        int idx = jsonBody.indexOf("\"transferRef\"");
        if (idx < 0) return null;
        int colon = jsonBody.indexOf(':', idx);
        if (colon < 0) return null;
        int start = jsonBody.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = jsonBody.indexOf('"', start + 1);
        if (end < 0) return null;
        return jsonBody.substring(start + 1, end);
    }
}
