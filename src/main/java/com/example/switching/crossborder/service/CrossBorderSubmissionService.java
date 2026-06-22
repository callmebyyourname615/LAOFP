package com.example.switching.crossborder.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.CrossBorderInitiateResponse;
import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;

@Service
public class CrossBorderSubmissionService {

    private final CrossBorderTransferService legacy;
    private final RailMessageJournalService journal;
    private final Environment environment;
    private final ObjectProvider<PushPaymentOrchestrator> orchestrator;
    private final JdbcTemplate jdbc;

    public CrossBorderSubmissionService(
            CrossBorderTransferService legacy,
            RailMessageJournalService journal,
            Environment environment,
            ObjectProvider<PushPaymentOrchestrator> orchestrator,
            JdbcTemplate jdbc) {
        this.legacy = legacy;
        this.journal = journal;
        this.environment = environment;
        this.orchestrator = orchestrator;
        this.jdbc = jdbc;
    }

    public CrossBorderInitiateResponse initiate(CrossBorderInitiateRequest request) {
        CrossBorderInitiateResponse response = orchestratorEnabled()
                ? orchestrated(request)
                : legacy.initiate(request);
        if (adaptersEnabled()) {
            String externalReference = response.networkTxnId() == null
                    ? response.txnRef()
                    : response.networkTxnId();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("cbId", String.valueOf(response.cbId()));
            evidence.put("txnRef", String.valueOf(response.txnRef()));
            if (response.sourceAmount() != null) {
                evidence.put("amount", response.sourceAmount().toPlainString());
            }
            if (response.sourceCurrency() != null) {
                evidence.put("currency", response.sourceCurrency());
            }
            UUIDHolder journalEntry = new UUIDHolder(journal.recordOutbound(
                    normalize(response.targetNetwork()),
                    externalReference,
                    response.txnRef(),
                    "BUSINESS_SUBMISSION",
                    Map.copyOf(evidence)));
            journal.complete(
                    journalEntry.value(),
                    Map.of("status", response.status()),
                    "COMPLETED",
                    "BUSINESS_FLOW");
        }
        return response;
    }

    private CrossBorderInitiateResponse orchestrated(CrossBorderInitiateRequest request) {
        PushPaymentOrchestrator available = orchestrator.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(
                    "Push-payment orchestrator is enabled but unavailable");
        }
        Map<String, Object> quote = jdbc.queryForMap("""
                SELECT source_amount, source_currency
                  FROM fx_quotes
                 WHERE quote_id=?
                """, request.quoteId());
        PushPaymentResult result = available.start(new PushPaymentRequest(
                PaymentChannel.CROSS_BORDER,
                "QUOTE-" + request.quoteId(),
                "CB-" + request.quoteId() + "-" + request.initiatingPspId(),
                request.initiatingPspId(),
                request.beneficiaryBank(),
                (java.math.BigDecimal) quote.get("source_amount"),
                String.valueOf(quote.get("source_currency")),
                payload(request)));
        if (!(result.channelResult() instanceof CrossBorderInitiateResponse response)) {
            throw new IllegalStateException(
                    "Cross-border lifecycle did not return its channel response");
        }
        return response;
    }

    private static Map<String, Object> payload(CrossBorderInitiateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("quoteId", request.quoteId().toString());
        put(payload, "initiatingPspId", request.initiatingPspId());
        put(payload, "beneficiaryName", request.beneficiaryName());
        put(payload, "beneficiaryBank", request.beneficiaryBank());
        put(payload, "beneficiaryAccount", request.beneficiaryAccount());
        put(payload, "beneficiaryCountry", request.beneficiaryCountry());
        put(payload, "purposeCode", request.purposeCode());
        put(payload, "sourceOfFunds", request.sourceOfFunds());
        return Map.copyOf(payload);
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private boolean orchestratorEnabled() {
        return environment.getProperty(
                "switching.phase-ii.push-payment-orchestrator.enabled",
                Boolean.class,
                false);
    }

    private boolean adaptersEnabled() {
        return environment.getProperty(
                "switching.phase-ii.cross-border-adapters.enabled",
                Boolean.class,
                false);
    }

    private static String normalize(String network) {
        String value = network == null
                ? ""
                : network.toUpperCase(java.util.Locale.ROOT);
        if (value.contains("NITMX") || value.contains("PROMPT")) {
            return "PROMPTPAY";
        }
        if (value.contains("BAKONG")) {
            return "BAKONG";
        }
        if (value.contains("NAPAS")) {
            return "NAPAS";
        }
        if (value.contains("UPI")) {
            return "UPI";
        }
        throw new IllegalArgumentException("Unsupported cross-border target network");
    }

    private record UUIDHolder(java.util.UUID value) {}
}
