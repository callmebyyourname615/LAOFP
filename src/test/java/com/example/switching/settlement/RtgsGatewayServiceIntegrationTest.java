package com.example.switching.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementInstructionEntity;
import com.example.switching.settlement.repository.SettlementInstructionRepository;
import com.example.switching.settlement.service.HighValueRtgsInstructionService;
import com.example.switching.settlement.service.RtgsGatewayService;
import com.example.switching.settlement.service.SettlementCycleService;
import com.example.switching.settlement.service.SettlementInstructionService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class RtgsGatewayServiceIntegrationTest extends AbstractIntegrationTest {

    private static final HttpServer RTGS_SERVER;
    private static final String RTGS_URL;
    private static final List<String> REQUEST_BODIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(200);

    static {
        try {
            RTGS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RTGS_SERVER.createContext("/rtgs/pacs009", RtgsGatewayServiceIntegrationTest::handleRtgs);
            RTGS_SERVER.start();
            RTGS_URL = "http://127.0.0.1:" + RTGS_SERVER.getAddress().getPort() + "/rtgs/pacs009";
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void overrideRtgsProperties(DynamicPropertyRegistry registry) {
        registry.add("switching.settlement.bol-rtgs-url", () -> RTGS_URL);
        registry.add("switching.settlement.rtgs-timeout-ms", () -> "5000");
        registry.add("switching.settlement.rtgs-callback-ip-whitelist", () -> "127.0.0.1");
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementCycleService cycleService;
    @Autowired private SettlementInstructionService instructionService;
    @Autowired private SettlementInstructionRepository instructionRepository;
    @Autowired private RtgsGatewayService rtgsGatewayService;
    @Autowired private HighValueRtgsInstructionService highValueRtgsInstructionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        REQUEST_BODIES.clear();
        RESPONSE_STATUS.set(200);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void sendApprovedInstruction_postsPacs009AndMarksSentRtgs() {
        SettlementInstructionEntity approved = approvedInstruction(LocalDate.now().plusDays(130));

        SettlementInstructionEntity sent =
                rtgsGatewayService.sendApprovedInstruction(approved.getInstructionRef(), "ops-user");

        assertEquals("SENT_RTGS", sent.getStatus());
        assertNotNull(sent.getRtgsMsgId());
        assertNotNull(sent.getSentAt());
        assertNotNull(sent.getRtgsRequestPayload());
        assertNull(sent.getLastError());
        assertEquals(1, REQUEST_BODIES.size());

        String xml = REQUEST_BODIES.getFirst();
        assertTrue(xml.contains("pacs.009.001.08"));
        assertTrue(xml.contains("<MsgId>" + sent.getRtgsMsgId() + "</MsgId>"));
        assertTrue(xml.contains("<InstrId>" + sent.getInstructionRef() + "</InstrId>"));
        assertTrue(xml.contains("<MmbId>BANK_A</MmbId>"));
        assertTrue(xml.contains("<MmbId>BANK_B</MmbId>"));
        assertTrue(xml.contains("<IntrBkSttlmAmt Ccy=\"LAK\">1500.00</IntrBkSttlmAmt>"));
    }

    @Test
    void sendApprovedInstruction_requiresApprovedState() {
        SettlementInstructionEntity pending = pendingInstruction(LocalDate.now().plusDays(131));

        assertThrows(IllegalStateException.class,
                () -> rtgsGatewayService.sendApprovedInstruction(pending.getInstructionRef(), "ops-user"));
        assertEquals(0, REQUEST_BODIES.size());
    }

    @Test
    void sendApprovedInstruction_non2xxKeepsApprovedAndStoresLastErrorForRetry() {
        RESPONSE_STATUS.set(503);
        SettlementInstructionEntity approved = approvedInstruction(LocalDate.now().plusDays(132));

        assertThrows(IllegalStateException.class,
                () -> rtgsGatewayService.sendApprovedInstruction(approved.getInstructionRef(), "ops-user"));

        SettlementInstructionEntity stored = instructionRepository
                .findByInstructionRef(approved.getInstructionRef())
                .orElseThrow();
        assertEquals("APPROVED", stored.getStatus());
        assertNotNull(stored.getRtgsMsgId());
        assertNotNull(stored.getRtgsRequestPayload());
        assertNotNull(stored.getRtgsResponsePayload());
        assertTrue(stored.getLastError().contains("HTTP 503"));
        assertEquals(1, REQUEST_BODIES.size());
    }

    @Test
    void rtgsCallback_confirmedMovesSentInstructionToConfirmedAndIsIdempotent() throws Exception {
        SettlementInstructionEntity sent = sentInstruction(LocalDate.now().plusDays(133));

        String callback = """
                {"instructionRef":"%s","rtgsMsgId":"%s","status":"CONFIRMED"}
                """.formatted(sent.getInstructionRef(), sent.getRtgsMsgId());

        mockMvc.perform(post("/v1/settlement/rtgs-callback")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType("application/json")
                        .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructionRef").value(sent.getInstructionRef()))
                .andExpect(jsonPath("$.rtgsMsgId").value(sent.getRtgsMsgId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/v1/settlement/rtgs-callback")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType("application/json")
                        .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        SettlementInstructionEntity stored = instructionRepository
                .findByInstructionRef(sent.getInstructionRef())
                .orElseThrow();
        assertEquals("CONFIRMED", stored.getStatus());
        assertNotNull(stored.getConfirmedAt());
        assertNull(stored.getLastError());
    }

    @Test
    void rtgsCallback_rejectedMovesSentInstructionToFailed() throws Exception {
        SettlementInstructionEntity sent = sentInstruction(LocalDate.now().plusDays(134));

        mockMvc.perform(post("/v1/settlement/rtgs-callback")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType("application/json")
                        .content("""
                                {"rtgsMsgId":"%s","status":"REJECTED","reason":"BOL rejected settlement"}
                                """.formatted(sent.getRtgsMsgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastError").value("BOL rejected settlement"));

        SettlementInstructionEntity stored = instructionRepository
                .findByInstructionRef(sent.getInstructionRef())
                .orElseThrow();
        assertEquals("FAILED", stored.getStatus());
        assertEquals("BOL rejected settlement", stored.getLastError());
    }

    @Test
    void rtgsCallback_rejectsIpOutsideWhitelist() throws Exception {
        SettlementInstructionEntity sent = sentInstruction(LocalDate.now().plusDays(135));

        mockMvc.perform(post("/v1/settlement/rtgs-callback")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType("application/json")
                        .content("""
                                {"instructionRef":"%s","rtgsMsgId":"%s","status":"CONFIRMED"}
                                """.formatted(sent.getInstructionRef(), sent.getRtgsMsgId())))
                .andExpect(status().isForbidden());

        SettlementInstructionEntity stored = instructionRepository
                .findByInstructionRef(sent.getInstructionRef())
                .orElseThrow();
        assertEquals("SENT_RTGS", stored.getStatus());
    }

    @Test
    void highValueInstruction_approvesSendsAndConfirmsThroughRtgsPath() throws Exception {
        String transferRef = "HV-E2E-" + System.nanoTime();
        seedHighValueSettledTransfer(transferRef);
        SettlementInstructionEntity pending =
                highValueRtgsInstructionService.generatePendingInstruction(transferRef);
        SettlementInstructionEntity approved =
                instructionService.approve(pending.getInstructionRef(), "checker-user", "high value checked");

        SettlementInstructionEntity sent =
                rtgsGatewayService.sendApprovedInstruction(approved.getInstructionRef(), "ops-user");

        assertEquals("SENT_RTGS", sent.getStatus());
        assertEquals("HIGH_VALUE_TRANSFER", sent.getSourceType());
        assertEquals(transferRef, sent.getTransferRef());
        assertEquals(1, REQUEST_BODIES.size());
        assertTrue(REQUEST_BODIES.getFirst().contains("<InstrId>" + sent.getInstructionRef() + "</InstrId>"));
        assertTrue(REQUEST_BODIES.getFirst().contains("<IntrBkSttlmAmt Ccy=\"LAK\">600000000.00</IntrBkSttlmAmt>"));

        mockMvc.perform(post("/v1/settlement/rtgs-callback")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType("application/json")
                        .content("""
                                {"instructionRef":"%s","rtgsMsgId":"%s","status":"CONFIRMED"}
                                """.formatted(sent.getInstructionRef(), sent.getRtgsMsgId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        SettlementInstructionEntity confirmed = instructionRepository
                .findByTransferRef(transferRef)
                .orElseThrow();
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertNotNull(confirmed.getConfirmedAt());
    }

    private SettlementInstructionEntity approvedInstruction(LocalDate settlementDate) {
        SettlementInstructionEntity instruction = pendingInstruction(settlementDate);
        return instructionService.approve(instruction.getInstructionRef(), "checker-user", "ready for RTGS");
    }

    private SettlementInstructionEntity sentInstruction(LocalDate settlementDate) {
        SettlementInstructionEntity approved = approvedInstruction(settlementDate);
        return rtgsGatewayService.sendApprovedInstruction(approved.getInstructionRef(), "ops-user");
    }

    private SettlementInstructionEntity pendingInstruction(LocalDate settlementDate) {
        SettlementCycleEntity cycle = closedCycleWithPositions(settlementDate);
        return instructionService.generateForCycle(cycle.getCycleRef()).getFirst();
    }

    private SettlementCycleEntity closedCycleWithPositions(LocalDate settlementDate) {
        SettlementCycleEntity cycle = cycleService.openCycle(settlementDate);
        jdbcTemplate.update("""
                INSERT INTO settlement_positions
                    (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count)
                VALUES (?, 'BANK_A', 'LAK', ?, 0.00, 1)
                """, cycle.getId(), new BigDecimal("1500.00"));
        jdbcTemplate.update("""
                INSERT INTO settlement_positions
                    (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count)
                VALUES (?, 'BANK_B', 'LAK', 0.00, ?, 1)
                """, cycle.getId(), new BigDecimal("1500.00"));
        return cycleService.closeCycle(cycle.getCycleRef());
    }

    private void seedHighValueSettledTransfer(String transferRef) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, client_transaction_id, idempotency_key, flow_ref, inquiry_ref,
                    source_bank, source_account_no, destination_bank, destination_account_no,
                    destination_account_name, amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference, settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, 'BANK_A', '010100000001', 'BANK_B', '020200000001',
                    'Receiver', 600000000.00, 'LAK', 'API', 'ROUTE_HV_RTGS', 'MOCK_BANK_B_CONNECTOR',
                    'SETTLED', ?, ?, 'RTGS', true, ?, ?, ?, ?)
                """,
                transferRef,
                transferRef,
                transferRef,
                transferRef,
                "INQ-" + transferRef,
                "EXT-" + transferRef,
                "REF-" + transferRef,
                LocalDate.now(),
                now,
                now,
                now);
    }

    private static void handleRtgs(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUEST_BODIES.add(body);
        int status = RESPONSE_STATUS.get();
        String response = status >= 200 && status < 300
                ? "<rtgsResponse status=\"ACCEPTED\"/>"
                : "<rtgsResponse status=\"REJECTED\"/>";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
