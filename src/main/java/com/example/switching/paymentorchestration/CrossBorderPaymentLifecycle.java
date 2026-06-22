package com.example.switching.paymentorchestration;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.CrossBorderInitiateResponse;
import com.example.switching.crossborder.service.CrossBorderTransferService;

@Component
public class CrossBorderPaymentLifecycle implements PaymentLifecycle {

    private final CrossBorderTransferService service;

    public CrossBorderPaymentLifecycle(CrossBorderTransferService service) {
        this.service = service;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.CROSS_BORDER;
    }

    @Override
    public Class<?> channelResultType() {
        return CrossBorderInitiateResponse.class;
    }

    @Override
    public PushPaymentResult execute(PushPaymentRequest request, PushPaymentPolicy policy) {
        CrossBorderInitiateRequest command = new CrossBorderInitiateRequest(
                Long.valueOf(TransferPaymentLifecycle.text(request.payload(), "quoteId")),
                TransferPaymentLifecycle.text(request.payload(), "initiatingPspId"),
                TransferPaymentLifecycle.text(request.payload(), "beneficiaryName"),
                TransferPaymentLifecycle.text(request.payload(), "beneficiaryBank"),
                TransferPaymentLifecycle.text(request.payload(), "beneficiaryAccount"),
                TransferPaymentLifecycle.text(request.payload(), "beneficiaryCountry"),
                TransferPaymentLifecycle.text(request.payload(), "purposeCode"),
                TransferPaymentLifecycle.text(request.payload(), "sourceOfFunds"));
        CrossBorderInitiateResponse response = service.initiate(command);
        PaymentExecutionStatus status = "COMPLETED".equalsIgnoreCase(response.status())
                ? PaymentExecutionStatus.SETTLED
                : PaymentExecutionStatus.ACCEPTED;
        return new PushPaymentResult(
                UUID.randomUUID(),
                response.txnRef(),
                status,
                response.status(),
                response);
    }
}
