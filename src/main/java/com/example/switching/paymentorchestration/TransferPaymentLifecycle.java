package com.example.switching.paymentorchestration;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;
import com.example.switching.transfer.service.CreateTransferService;

@Component
public class TransferPaymentLifecycle implements PaymentLifecycle {

    private final CreateTransferService transfers;

    public TransferPaymentLifecycle(CreateTransferService transfers) {
        this.transfers = transfers;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.TRANSFER;
    }

    @Override
    public Class<?> channelResultType() {
        return CreateTransferResponse.class;
    }

    @Override
    public PushPaymentResult execute(PushPaymentRequest request, PushPaymentPolicy policy) {
        CreateTransferResponse response = transfers.create(toTransferRequest(request.payload()));
        return new PushPaymentResult(
                UUID.randomUUID(),
                response.getTransferRef(),
                map(response.getStatus()),
                response.getMessage(),
                response);
    }

    static CreateTransferRequest toTransferRequest(Map<String, Object> payload) {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setSourceBank(text(payload, "sourceBank"));
        request.setDestinationBank(text(payload, "destinationBank"));
        request.setDebtorAccount(text(payload, "debtorAccount"));
        request.setCreditorAccount(text(payload, "creditorAccount"));
        request.setAmount(decimal(payload, "amount"));
        request.setCurrency(text(payload, "currency"));
        request.setReference(text(payload, "reference"));
        request.setIdempotencyKey(text(payload, "idempotencyKey"));
        request.setInquiryRef(text(payload, "inquiryRef"));
        request.setBeneficiaryToken(text(payload, "beneficiaryToken"));
        return request;
    }

    static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static BigDecimal decimal(Map<String, Object> payload, String key) {
        String value = text(payload, key);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static PaymentExecutionStatus map(String status) {
        if (status == null) {
            return PaymentExecutionStatus.PENDING;
        }
        try {
            return PaymentExecutionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PaymentExecutionStatus.ACCEPTED;
        }
    }
}
