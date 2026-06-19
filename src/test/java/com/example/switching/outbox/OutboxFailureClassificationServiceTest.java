package com.example.switching.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.switching.common.error.ErrorCatalog;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.enums.FailureClass;
import com.example.switching.outbox.service.OutboxFailureClassificationService;

class OutboxFailureClassificationServiceTest {

    private final OutboxFailureClassificationService service = new OutboxFailureClassificationService();

    @Test
    void technicalNetworkFailureIsTransientAndRetryable() {
        FailureClass failureClass = service.classifyTechnicalFailure(ErrorCatalog.NET_002);

        assertEquals(FailureClass.TRANSIENT, failureClass);
        assertTrue(service.shouldRetry(failureClass, 1, 3));
    }

    @Test
    void downstreamRejectIsPermanentBusinessAndNotRetryable() {
        FailureClass failureClass = service.classifyBankFailure(
                BankDispatchResult.failed("PACS002-RJCT", "PACS.002 rejected. reasonCode=AC04"));

        assertEquals(FailureClass.PERMANENT_BUSINESS, failureClass);
        assertFalse(service.shouldRetry(failureClass, 1, 3));
        assertTrue(service.shouldRejectTransfer(failureClass, false));
    }

    @Test
    void complianceRejectIsPermanentCompliance() {
        FailureClass failureClass = service.classifyBankFailure(
                BankDispatchResult.failed("AML-001", "AML sanction screening rejected transfer"));

        assertEquals(FailureClass.PERMANENT_COMPLIANCE, failureClass);
        assertFalse(service.shouldRetry(failureClass, 1, 3));
        assertTrue(service.shouldRejectTransfer(failureClass, false));
    }

    @Test
    void unknownPacs002StatusIsAmbiguousAndNotRejectedImmediately() {
        FailureClass failureClass = service.classifyBankFailure(
                BankDispatchResult.failed("PACS002-UNKNOWN", "Unsupported PACS.002 TxSts=PDNG"));

        assertEquals(FailureClass.AMBIGUOUS, failureClass);
        assertTrue(service.shouldRetry(failureClass, 1, 3));
        assertFalse(service.shouldRejectTransfer(failureClass, false));
    }
}
