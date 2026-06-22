package com.example.switching.rtp.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;
import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;
import com.example.switching.transfer.service.CreateTransferService;

@Component
@ConditionalOnProperty(prefix = "switching.phase-ii.rtp", name = "enabled", havingValue = "true")
public class TransferRtpSettlementGateway implements RtpSettlementGateway {

    private final CreateTransferService transfers;
    private final ObjectProvider<PushPaymentOrchestrator> orchestrator;
    private final Environment environment;

    public TransferRtpSettlementGateway(
            CreateTransferService transfers,
            ObjectProvider<PushPaymentOrchestrator> orchestrator,
            Environment environment) {
        this.transfers = transfers;
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    @Override
    public SettlementSubmission submit(SettlementCommand command) {
        if (command.inquiryRef() == null || command.inquiryRef().isBlank()) {
            throw new IllegalArgumentException(
                    "inquiryRef is required for RTP settlement through the transfer rail");
        }
        CreateTransferRequest transferRequest = toTransfer(command);
        if (orchestratorEnabled()) {
            PushPaymentOrchestrator available = orchestrator.getIfAvailable();
            if (available == null) {
                throw new IllegalStateException(
                        "Push-payment orchestrator is enabled but unavailable");
            }
            PushPaymentResult result = available.start(new PushPaymentRequest(
                    PaymentChannel.RTP,
                    command.reference(),
                    command.idempotencyKey(),
                    command.sourceParticipant(),
                    command.destinationParticipant(),
                    command.amount(),
                    command.currency(),
                    transferPayload(transferRequest)));
            if (result.channelResult() instanceof CreateTransferResponse response) {
                return new SettlementSubmission(
                        response.getTransferRef(),
                        response.getStatus(),
                        response.getMessage());
            }
            return new SettlementSubmission(
                    result.externalReference(),
                    result.status().name(),
                    result.message());
        }
        CreateTransferResponse response = transfers.create(transferRequest);
        return new SettlementSubmission(
                response.getTransferRef(), response.getStatus(), response.getMessage());
    }

    private boolean orchestratorEnabled() {
        return environment.getProperty(
                "switching.phase-ii.push-payment-orchestrator.enabled",
                Boolean.class,
                false);
    }

    private static Map<String, Object> transferPayload(CreateTransferRequest request) {
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

    private static CreateTransferRequest toTransfer(SettlementCommand command) {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setSourceBank(command.sourceParticipant());
        request.setDestinationBank(command.destinationParticipant());
        request.setDebtorAccount(command.payerAccount());
        request.setCreditorAccount(command.payeeAccount());
        request.setAmount(command.amount());
        request.setCurrency(command.currency());
        request.setInquiryRef(command.inquiryRef());
        request.setIdempotencyKey(command.idempotencyKey());
        request.setReference(command.reference());
        return request;
    }
}
