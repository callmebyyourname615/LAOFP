package com.example.switching.operations.service;

import com.example.switching.AbstractIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OperationsIsoInquiryQueryIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(6000);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();
    }

    @Test
    void searchIsoInquiriesByInquiryRefReturnsOperationFields() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = "INQ-OPS-" + suffix;
        String messageId = "ACMT023-OPS-" + suffix;

        insertIsoInquiry(
                inquiryRef,
                messageId,
                "ELIGIBLE",
                LocalDateTime.now().plusMinutes(10),
                null
        );

        MvcResult result = mockMvc.perform(get("/api/operations/iso-inquiries")
                        .param("inquiryRef", inquiryRef)
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstItem = root.path("items").get(0);

        assertEquals("HAS_ISO_INQUIRIES", root.path("status").asText());
        assertEquals(1, root.path("returnedItems").asInt());

        assertEquals(inquiryRef, firstItem.path("inquiryRef").asText());
        assertEquals(messageId, firstItem.path("messageId").asText());
        assertEquals("ELIGIBLE", firstItem.path("status").asText());
        assertTrue(firstItem.path("debtorAccount").isNull());
        assertEquals("********0001", firstItem.path("creditorAccount").asText());
        assertEquals("/api/iso-inquiries/" + inquiryRef, firstItem.path("inquiryApiPath").asText());
        assertTrue(firstItem.path("transferApiPath").isNull());
    }

    @Test
    void detailIsoInquiryReturns404ForMissingInquiryRef() throws Exception {
        mockMvc.perform(get("/api/operations/iso-inquiries/{inquiryRef}", "INQ-OPS-NOT-FOUND"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailIsoInquiryReturnsItemForInquiryRef() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = "INQ-OPS-DETAIL-" + suffix;
        String messageId = "ACMT023-OPS-DETAIL-" + suffix;
        String transferRef = "TRX-OPS-" + suffix;

        insertIsoInquiry(
                inquiryRef,
                messageId,
                "USED",
                LocalDateTime.now().plusMinutes(10),
                transferRef
        );

        MvcResult result = mockMvc.perform(get("/api/operations/iso-inquiries/{inquiryRef}", inquiryRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(inquiryRef, root.path("inquiryRef").asText());
        assertEquals("USED", root.path("status").asText());
        assertEquals(transferRef, root.path("usedByTransferRef").asText());
        assertEquals("/api/transfers/" + transferRef, root.path("transferApiPath").asText());
    }

    @Test
    void expiredFilterReturnsExpiredInquiry() throws Exception {
        String suffix = uniqueSuffix();
        String inquiryRef = "INQ-OPS-EXP-" + suffix;
        String messageId = "ACMT023-OPS-EXP-" + suffix;

        insertIsoInquiry(
                inquiryRef,
                messageId,
                "ELIGIBLE",
                LocalDateTime.now().minusMinutes(10),
                null
        );

        MvcResult result = mockMvc.perform(get("/api/operations/iso-inquiries")
                        .param("inquiryRef", inquiryRef)
                        .param("expired", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstItem = root.path("items").get(0);

        assertEquals(1, root.path("returnedItems").asInt());
        assertEquals(inquiryRef, firstItem.path("inquiryRef").asText());
        assertTrue(firstItem.path("expired").asBoolean());
    }

    private void insertIsoInquiry(
            String inquiryRef,
            String messageId,
            String status,
            LocalDateTime expiresAt,
            String usedByTransferRef
    ) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                """
                INSERT INTO inquiries (
                    inquiry_ref,
                    channel_id,
                    message_id,
                    instruction_id,
                    end_to_end_id,
                    source_bank,
                    destination_bank,
                    debtor_account,
                    creditor_account,
                    amount,
                    currency,
                    reference,
                    status,
                    account_found,
                    bank_available,
                    eligible_for_transfer,
                    error_code,
                    error_message,
                    expires_at,
                    used_by_transaction_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """,
                inquiryRef,
                "ISO20022_XML",
                messageId,
                "VERIFY-" + inquiryRef,
                "E2E-" + inquiryRef,
                "BANK_A",
                "BANK_B",
                null,
                "020200000001",
                new BigDecimal("150000.00"),
                "LAK",
                "Operations ISO inquiry query test " + inquiryRef,
                status,
                true,
                true,
                "ELIGIBLE".equals(status),
                null,
                null,
                expiresAt,
                usedByTransferRef,
                now,
                now
        );
    }

    private String uniqueSuffix() {
        int sequence = SUFFIX_SEQUENCE.incrementAndGet();
        long millisPart = Math.floorMod(System.currentTimeMillis(), 10_000L);
        return millisPart + "-" + sequence;
    }
}
