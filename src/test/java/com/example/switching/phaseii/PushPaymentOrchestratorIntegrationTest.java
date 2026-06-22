package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.common.PhaseIIAuditPublisher;
import com.example.switching.paymentorchestration.PaymentChannel;
import com.example.switching.paymentorchestration.PaymentExecutionStatus;
import com.example.switching.paymentorchestration.PaymentLifecycle;
import com.example.switching.paymentorchestration.PushPaymentOrchestrator;
import com.example.switching.paymentorchestration.PushPaymentPolicy;
import com.example.switching.paymentorchestration.PushPaymentPolicyRepository;
import com.example.switching.paymentorchestration.PushPaymentRequest;
import com.example.switching.paymentorchestration.PushPaymentResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class PushPaymentOrchestratorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void cleanExecutionJournal() {
        jdbc.update("DELETE FROM push_payment_transition");
        jdbc.update("DELETE FROM push_payment_execution");
    }

    @Test
    void repeatedIdempotencyKeyReturnsPersistedTypedResultWithoutReexecution() {
        AtomicInteger executions = new AtomicInteger();
        PaymentLifecycle lifecycle = new PaymentLifecycle() {
            @Override
            public PaymentChannel channel() {
                return PaymentChannel.TRANSFER;
            }

            @Override
            public Class<?> channelResultType() {
                return TestChannelResult.class;
            }

            @Override
            public PushPaymentResult execute(
                    PushPaymentRequest request,
                    PushPaymentPolicy policy) {
                executions.incrementAndGet();
                return new PushPaymentResult(
                        UUID.randomUUID(),
                        "TXN-PHASE-II-001",
                        PaymentExecutionStatus.SETTLED,
                        "SETTLED",
                        new TestChannelResult("TXN-PHASE-II-001", "SETTLED"));
            }
        };

        PushPaymentOrchestrator orchestrator = new PushPaymentOrchestrator(
                jdbc,
                new TransactionTemplate(transactionManager),
                new PushPaymentPolicyRepository(jdbc, mapper),
                List.of(lifecycle),
                mapper,
                mock(PhaseIIAuditPublisher.class));

        PushPaymentRequest request = new PushPaymentRequest(
                PaymentChannel.TRANSFER,
                "BUSINESS-001",
                "IDEMPOTENCY-001",
                "BANK_A",
                "BANK_B",
                new BigDecimal("1000.0000"),
                "LAK",
                Map.of("reference", "BUSINESS-001"));

        PushPaymentResult first = orchestrator.start(request);
        PushPaymentResult replay = orchestrator.start(request);

        assertEquals(first.executionId(), replay.executionId());
        assertEquals(1, executions.get());
        TestChannelResult restored = assertInstanceOf(
                TestChannelResult.class,
                replay.channelResult());
        assertEquals("TXN-PHASE-II-001", restored.reference());
    }

    public record TestChannelResult(String reference, String status) {}
}
