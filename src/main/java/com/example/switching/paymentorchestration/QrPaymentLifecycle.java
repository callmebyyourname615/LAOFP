package com.example.switching.paymentorchestration;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.switching.qr.dto.QrPayResponse;
import com.example.switching.qr.service.QrPaymentService;

@Component
public class QrPaymentLifecycle implements PaymentLifecycle {

    private final QrPaymentService service;

    public QrPaymentLifecycle(QrPaymentService service) {
        this.service = service;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.QR;
    }

    @Override
    public Class<?> channelResultType() {
        return QrPayResponse.class;
    }

    @Override
    public PushPaymentResult execute(PushPaymentRequest request, PushPaymentPolicy policy) {
        String qrId = TransferPaymentLifecycle.text(request.payload(), "qrId");
        String issuer = TransferPaymentLifecycle.text(request.payload(), "issuingPspId");
        BigDecimal amount = TransferPaymentLifecycle.decimal(request.payload(), "amount");
        QrPayResponse response = service.pay(qrId, issuer, amount);
        return new PushPaymentResult(
                UUID.randomUUID(),
                response.txnId(),
                PaymentExecutionStatus.SETTLED,
                response.status(),
                response);
    }
}
