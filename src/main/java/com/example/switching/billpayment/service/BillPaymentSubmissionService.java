package com.example.switching.billpayment.service;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;

@Service
public class BillPaymentSubmissionService {

    private final BillPaymentService legacy;
    private final ObjectProvider<PushPaymentOrchestrator> orchestrator;
    private final Environment environment;

    public BillPaymentSubmissionService(
            BillPaymentService legacy,
            ObjectProvider<PushPaymentOrchestrator> orchestrator,
            Environment environment) {
        this.legacy = legacy;
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    public BillPayResponse pay(Long tokenId, String payingPspId) {
        if (!enabled()) {
            return legacy.pay(tokenId, payingPspId);
        }
        if (tokenId == null || payingPspId == null || payingPspId.isBlank()) {
            throw new IllegalArgumentException("Bill token and paying PSP are required");
        }
        PushPaymentResult result = requiredOrchestrator().start(new PushPaymentRequest(
                PaymentChannel.BILL,
                tokenId.toString(),
                "BILL-" + tokenId + "-" + payingPspId,
                payingPspId,
                "BILLER",
                null,
                null,
                Map.of(
                        "tokenId", tokenId.toString(),
                        "payingPspId", payingPspId)));
        if (!(result.channelResult() instanceof BillPayResponse response)) {
            throw new IllegalStateException("Bill lifecycle did not return its channel response");
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
}
