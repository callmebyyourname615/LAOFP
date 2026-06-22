package com.example.switching.paymentorchestration;

public interface PaymentLifecycle {

    PaymentChannel channel();

    PushPaymentResult execute(
            PushPaymentRequest request,
            PushPaymentPolicy policy);

    default Class<?> channelResultType() {
        return Object.class;
    }
}
