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

class OperationsTransferQueryIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SUFFIX_SEQUENCE = new AtomicInteger(7000);

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
    void searchTransfersByTransferRefReturnsOperationsFields() throws Exception {
        String suffix = uniqueSuffix();
        String transferRef = "TRX-OPS-" + suffix;
        String inquiryRef = "INQ-OPS-" + suffix;

        insertTransfer(
                transferRef,
                inquiryRef,
                "SUCCESS",
                "BANK_A",
                "BANK_B",
                "010100000001",
                "020200000001",
                "ISO20022_XML",
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                "MOCK_BANK_B_CONNECTOR",
                "EXT-" + suffix,
                null,
                null
        );

        MvcResult result = mockMvc.perform(get("/api/operations/transfers")
                        .param("transferRef", transferRef)
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstItem = root.path("items").get(0);

        assertEquals("HAS_TRANSFERS", root.path("status").asText());
        assertEquals(1, root.path("returnedItems").asInt());

        assertEquals(transferRef, firstItem.path("transferRef").asText());
        assertEquals(inquiryRef, firstItem.path("inquiryRef").asText());
        assertEquals("SUCCESS", firstItem.path("status").asText());
        assertEquals("SUCCESS", firstItem.path("currentStatus").asText());
        assertEquals("BANK_A", firstItem.path("sourceBank").asText());
        assertEquals("BANK_B", firstItem.path("destinationBank").asText());
        assertEquals("********0001", firstItem.path("debtorAccount").asText());
        assertEquals("********0001", firstItem.path("creditorAccount").asText());
        assertEquals("ISO20022_XML", firstItem.path("channelId").asText());
        assertEquals("ROUTE_BANK_A_TO_BANK_B_PACS008", firstItem.path("routeCode").asText());
        assertEquals("MOCK_BANK_B_CONNECTOR", firstItem.path("connectorName").asText());
        assertEquals("/api/transfers/" + transferRef, firstItem.path("transferApiPath").asText());
        assertEquals("/api/transfers/" + transferRef + "/trace", firstItem.path("traceApiPath").asText());
        assertEquals("/api/iso-inquiries/" + inquiryRef, firstItem.path("inquiryApiPath").asText());
    }

    @Test
    void detailTransferReturns404ForMissingTransferRef() throws Exception {
        mockMvc.perform(get("/api/operations/transfers/{transferRef}", "TRX-OPS-NOT-FOUND"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailTransferReturnsItemForTransferRef() throws Exception {
        String suffix = uniqueSuffix();
        String transferRef = "TRX-OPS-DETAIL-" + suffix;
        String inquiryRef = "INQ-OPS-DETAIL-" + suffix;

        insertTransfer(
                transferRef,
                inquiryRef,
                "SUCCESS",
                "BANK_A",
                "BANK_B",
                "010100000001",
                "020200000001",
                "ISO20022_XML",
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                "MOCK_BANK_B_CONNECTOR",
                "EXT-DETAIL-" + suffix,
                null,
                null
        );

        MvcResult result = mockMvc.perform(get("/api/operations/transfers/{transferRef}", transferRef))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(transferRef, root.path("transferRef").asText());
        assertEquals(inquiryRef, root.path("inquiryRef").asText());
        assertEquals("SUCCESS", root.path("currentStatus").asText());
        assertEquals("/api/transfers/" + transferRef + "/trace", root.path("traceApiPath").asText());
    }

    @Test
    void searchTransfersByStatusAndBankReturnsMatchingRows() throws Exception {
        String suffix = uniqueSuffix();
        String transferRef = "TRX-OPS-FILTER-" + suffix;
        String inquiryRef = "INQ-OPS-FILTER-" + suffix;

        insertTransfer(
                transferRef,
                inquiryRef,
                "FAILED",
                "BANK_A",
                "BANK_B",
                "010100000001",
                "020200000001",
                "ISO20022_XML",
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                "MOCK_BANK_B_CONNECTOR",
                "EXT-FILTER-" + suffix,
                "MS03",
                "Filter test failure"
        );

        MvcResult result = mockMvc.perform(get("/api/operations/transfers")
                        .param("status", "FAILED")
                        .param("bankCode", "BANK_A")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.path("items");

        boolean found = false;
        for (JsonNode item : items) {
            if (transferRef.equals(item.path("transferRef").asText())) {
                found = true;
                assertEquals("FAILED", item.path("status").asText());
                assertEquals("MS03", item.path("errorCode").asText());
                assertEquals("Filter test failure", item.path("errorMessage").asText());
            }
        }

        assertTrue(found, "Expected FAILED transfer to be returned by status + bank filter");
    }

    @Test
    void searchTransfersByConnectorAndAccountReturnsMatchingRows() throws Exception {
        String suffix = uniqueSuffix();
        String transferRef = "TRX-OPS-CONN-" + suffix;
        String inquiryRef = "INQ-OPS-CONN-" + suffix;

        insertTransfer(
                transferRef,
                inquiryRef,
                "SUCCESS",
                "BANK_A",
                "BANK_B",
                "010100000009",
                "020200000009",
                "ISO20022_XML",
                "ROUTE_BANK_A_TO_BANK_B_PACS008",
                "MOCK_BANK_B_CONNECTOR",
                "EXT-CONN-" + suffix,
                null,
                null
        );

        MvcResult result = mockMvc.perform(get("/api/operations/transfers")
                        .param("connectorName", "MOCK_BANK_B_CONNECTOR")
                        .param("creditorAccount", "020200000009")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.path("items");

        boolean found = false;
        for (JsonNode item : items) {
            if (transferRef.equals(item.path("transferRef").asText())) {
                found = true;
                assertEquals("********0009", item.path("creditorAccount").asText());
                assertEquals("MOCK_BANK_B_CONNECTOR", item.path("connectorName").asText());
            }
        }

        assertTrue(found, "Expected transfer to be returned by connectorName + creditorAccount filter");
    }

    private void insertTransfer(
            String transferRef,
            String inquiryRef,
            String status,
            String sourceBank,
            String destinationBank,
            String debtorAccount,
            String creditorAccount,
            String channelId,
            String routeCode,
            String connectorName,
            String externalReference,
            String errorCode,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = transferRef.replace("TRX-OPS-", "");

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    transaction_ref,
                    client_transaction_id,
                    idempotency_key,
                    source_bank,
                    source_account_no,
                    destination_bank,
                    destination_account_no,
                    destination_account_name,
                    amount,
                    currency,
                    channel_id,
                    route_code,
                    connector_name,
                    external_reference,
                    status,
                    error_code,
                    error_message,
                    reference,
                    inquiry_ref,
                    business_date,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                """,
                transferRef,
                "CLIENT-" + suffix,
                "IDEMP-" + suffix,
                sourceBank,
                debtorAccount,
                destinationBank,
                creditorAccount,
                "Receiver " + creditorAccount,
                new BigDecimal("150000.00"),
                "LAK",
                channelId,
                routeCode,
                connectorName,
                externalReference,
                status,
                errorCode,
                errorMessage,
                "Operations transfer query test " + transferRef,
                inquiryRef,
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
