package com.example.switching.paymentorchestration;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.billpayment.service.BillPaymentService;

@Component
public class BillPaymentLifecycle implements PaymentLifecycle {

    private final BillPaymentService service;

    public BillPaymentLifecycle(BillPaymentService service) {
        this.service = service;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.BILL;
    }

    @Override
    public Class<?> channelResultType() {
        return BillPayResponse.class;
    }

    @Override
    public PushPaymentResult execute(PushPaymentRequest request, PushPaymentPolicy policy) {
        Long tokenId = Long.valueOf(
                TransferPaymentLifecycle.text(request.payload(), "tokenId"));
        String payingPspId = TransferPaymentLifecycle.text(
                request.payload(), "payingPspId");
        BillPayResponse response = service.pay(tokenId, payingPspId);
        return new PushPaymentResult(
                UUID.randomUUID(),
                response.txnRef(),
                PaymentExecutionStatus.SETTLED,
                response.status(),
                response);
    }
}
