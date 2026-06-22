package com.example.switching.qr.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;
import com.example.switching.qr.dto.QrPayResponse;

@Service
public class QrPaymentSubmissionService {

    private final QrPaymentService legacy;
    private final ObjectProvider<PushPaymentOrchestrator> orchestrator;
    private final Environment environment;

    public QrPaymentSubmissionService(
            QrPaymentService legacy,
            ObjectProvider<PushPaymentOrchestrator> orchestrator,
            Environment environment) {
        this.legacy = legacy;
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    public QrPayResponse pay(String qrId, String issuingPspId, BigDecimal amount) {
        if (!enabled()) {
            return legacy.pay(qrId, issuingPspId, amount);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qrId", required(qrId, "qrId"));
        payload.put("issuingPspId", required(issuingPspId, "issuingPspId"));
        if (amount != null) {
            payload.put("amount", amount.toPlainString());
        }
        PushPaymentResult result = requiredOrchestrator().start(new PushPaymentRequest(
                PaymentChannel.QR,
                qrId,
                "QR-" + qrId + "-" + issuingPspId,
                issuingPspId,
                "QR_ACQUIRER",
                amount,
                "LAK",
                Map.copyOf(payload)));
        if (!(result.channelResult() instanceof QrPayResponse response)) {
            throw new IllegalStateException("QR lifecycle did not return its channel response");
        }
        return response;
    }

    private boolean enabled() {
        return environment.getProperty(
                "switching.phase-ii.push-payment-orchestrator.enabled",
                Boolean.class,
                false);
    }

    private PushPaymentOrchestrator requiredOrchestrator() {
        PushPaymentOrchestrator available = orchestrator.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(
                    "Push-payment orchestrator is enabled but unavailable");
        }
        return available;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
