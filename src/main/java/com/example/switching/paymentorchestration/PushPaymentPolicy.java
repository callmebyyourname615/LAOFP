package com.example.switching.paymentorchestration;
import java.time.Duration;import java.util.List;import java.util.Map;import java.util.UUID;
public record PushPaymentPolicy(UUID id,PaymentChannel channel,int version,Duration timeout,List<Duration> retrySchedule,
        FinalityMode finalityMode,Map<String,String> webhookEvents,Duration idempotencyTtl) {}
