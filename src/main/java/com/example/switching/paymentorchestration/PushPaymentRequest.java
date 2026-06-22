package com.example.switching.paymentorchestration;
import java.math.BigDecimal;import java.util.Map;
public record PushPaymentRequest(PaymentChannel channel,String businessReference,String idempotencyKey,
        String sourceParticipant,String destinationParticipant,BigDecimal amount,String currency,Map<String,Object> payload) {}
