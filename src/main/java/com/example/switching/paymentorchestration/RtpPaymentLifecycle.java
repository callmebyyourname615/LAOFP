package com.example.switching.paymentorchestration;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;
import com.example.switching.transfer.service.CreateTransferService;

@Component
public class RtpPaymentLifecycle implements PaymentLifecycle {

    private final CreateTransferService transfers;

    public RtpPaymentLifecycle(CreateTransferService transfers) {
        this.transfers = transfers;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.RTP;
    }

    @Override
    public Class<?> channelResultType() {
        return CreateTransferResponse.class;
    }

    @Override
    public PushPaymentResult execute(PushPaymentRequest request, PushPaymentPolicy policy) {
        CreateTransferRequest command = TransferPaymentLifecycle.toTransferRequest(request.payload());
        CreateTransferResponse response = transfers.create(command);
        PaymentExecutionStatus status = "SETTLED".equalsIgnoreCase(response.getStatus())
                ? PaymentExecutionStatus.SETTLED
                : PaymentExecutionStatus.ACCEPTED;
        return new PushPaymentResult(
                UUID.randomUUID(),
                response.getTransferRef(),
                status,
                response.getMessage(),
                response);
    }
}
