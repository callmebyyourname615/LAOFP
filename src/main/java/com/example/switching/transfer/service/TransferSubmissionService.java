package com.example.switching.transfer.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;
import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;

@Service
public class TransferSubmissionService {

    private final CreateTransferService legacy;
    private final ObjectProvider<PushPaymentOrchestrator> orchestrator;
    private final Environment environment;

    public TransferSubmissionService(
            CreateTransferService legacy,
            ObjectProvider<PushPaymentOrchestrator> orchestrator,
            Environment environment) {
        this.legacy = legacy;
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    public CreateTransferResponse create(CreateTransferRequest request) {
        if (!orchestratorEnabled()) {
            return legacy.create(request);
        }
        PushPaymentResult result = requiredOrchestrator().start(new PushPaymentRequest(
                PaymentChannel.TRANSFER,
                required(request.getInquiryRef(), "inquiryRef"),
                required(request.getIdempotencyKey(), "idempotencyKey"),
                request.getSourceBank(),
                request.getDestinationBank(),
                request.getAmount(),
                request.getCurrency(),
                payload(request)));
        if (!(result.channelResult() instanceof CreateTransferResponse response)) {
            throw new IllegalStateException("Transfer lifecycle did not return its channel response");
        }
        return response;
    }

    private PushPaymentOrchestrator requiredOrchestrator() {
        PushPaymentOrchestrator available = orchestrator.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(
                    "Push-payment orchestrator is enabled but unavailable");
        }
        return available;
    }

    private boolean orchestratorEnabled() {
        return environment.getProperty(
                "switching.phase-ii.push-payment-orchestrator.enabled",
                Boolean.class,
                false);
    }

    private static Map<String, Object> payload(CreateTransferRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "sourceBank", request.getSourceBank());
        put(payload, "destinationBank", request.getDestinationBank());
        put(payload, "debtorAccount", request.getDebtorAccount());
        put(payload, "creditorAccount", request.getCreditorAccount());
        put(payload, "amount", request.getAmount() == null
                ? null : request.getAmount().toPlainString());
        put(payload, "currency", request.getCurrency());
        put(payload, "reference", request.getReference());
        put(payload, "idempotencyKey", request.getIdempotencyKey());
        put(payload, "inquiryRef", request.getInquiryRef());
        put(payload, "beneficiaryToken", request.getBeneficiaryToken());
        return Map.copyOf(payload);
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required transfer value is blank: " + name);
        }
        return value;
    }
}
