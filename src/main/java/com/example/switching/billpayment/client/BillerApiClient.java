package com.example.switching.billpayment.client;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.switching.billpayment.exception.BillNotFoundException;
import com.example.switching.billpayment.exception.BillerTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client that talks to external biller APIs.
 *
 * <p>Contract expected from every biller:
 * <ul>
 *   <li>GET  {apiUrl}/bills/{billRef}  → 200 JSON BillDetail | 404</li>
 *   <li>POST {apiUrl}/payments         → 200 JSON {receiptNumber} | 5xx on failure</li>
 * </ul>
 *
 * All requests include {@code X-API-Key: {apiKeyHash}} for biller-side auth.
 * Per-request timeout is driven by the biller's {@code timeout_seconds} column.
 */
@Component
public class BillerApiClient {

    private static final Logger log = LoggerFactory.getLogger(BillerApiClient.class);

    private final HttpClient  httpClient;
    private final ObjectMapper mapper;

    public BillerApiClient(ObjectMapper mapper) {
        this.mapper     = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Bill fetch ────────────────────────────────────────────────────────────

    /**
     * Fetch bill detail from a biller.
     *
     * @return {@link BillerBillDto} populated from the biller JSON response
     * @throws BillNotFoundException   if biller returns 404
     * @throws BillerTimeoutException  if biller is unreachable or returns 5xx
     */
    public BillerBillDto fetchBill(String apiUrl, String apiKeyHash,
                                   String billRef, int timeoutSeconds) {
        URI uri = URI.create(apiUrl + "/bills/" + billRef);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("X-API-Key", apiKeyHash)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 404) {
                throw new BillNotFoundException(billRef);
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Biller fetchBill HTTP {} for ref={}", resp.statusCode(), billRef);
                throw new BillerTimeoutException("fetch:" + billRef);
            }
            return parseBillDetail(resp.body(), billRef);
        } catch (BillNotFoundException | BillerTimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Biller fetchBill error for ref={}: {}", billRef, ex.getMessage());
            throw new BillerTimeoutException("fetch:" + billRef);
        }
    }

    // ── Payment confirm ───────────────────────────────────────────────────────

    /**
     * Send payment instruction to the biller and get back a receipt number.
     *
     * @return receipt number string from biller
     * @throws BillerTimeoutException if biller returns non-2xx or times out
     */
    public String confirmPayment(String apiUrl, String apiKeyHash,
                                 String billRef, BigDecimal amount,
                                 String payingPspId, int timeoutSeconds) {
        String body = """
                {"billRef":"%s","amount":%s,"payingPspId":"%s"}
                """.formatted(billRef, amount.toPlainString(), payingPspId).trim();

        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl + "/payments"))
                .header("X-API-Key", apiKeyHash)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Biller confirmPayment HTTP {} for ref={}", resp.statusCode(), billRef);
                throw new BillerTimeoutException(billRef);
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("receiptNumber").asText("RECEIPT-" + System.nanoTime());
        } catch (BillerTimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Biller confirmPayment error for ref={}: {}", billRef, ex.getMessage());
            throw new BillerTimeoutException(billRef);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BillerBillDto parseBillDetail(String json, String billRef) {
        try {
            JsonNode n = mapper.readTree(json);
            BigDecimal amount   = new BigDecimal(n.path("amount").asText("0"));
            String dueDateStr   = n.path("dueDate").asText(null);
            LocalDate dueDate   = (dueDateStr != null && !dueDateStr.isBlank())
                                  ? LocalDate.parse(dueDateStr) : null;
            String customerName = n.path("customerName").asText(null);
            return new BillerBillDto(billRef, amount, dueDate, customerName);
        } catch (Exception e) {
            log.warn("Biller response parse error for ref={}: {}", billRef, e.getMessage());
            throw new BillerTimeoutException("parse:" + billRef);
        }
    }

    // ── Internal transfer object ───────────────────────────────────────────────

    public record BillerBillDto(
            String     billRef,
            BigDecimal amount,
            LocalDate  dueDate,
            String     customerName
    ) {}
}
