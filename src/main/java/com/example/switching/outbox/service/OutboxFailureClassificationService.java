package com.example.switching.outbox.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.switching.common.error.ErrorCatalog;
import com.example.switching.common.error.ErrorCategory;
import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.enums.FailureClass;

@Service
public class OutboxFailureClassificationService {

    public FailureClass classifyTechnicalFailure(ErrorCatalog catalog) {
        if (catalog == null) {
            return FailureClass.AMBIGUOUS;
        }

        if (catalog.getCategory() == ErrorCategory.NETWORK || catalog.isRetryable()) {
            return FailureClass.TRANSIENT;
        }

        if (catalog == ErrorCatalog.SYS_001) {
            return FailureClass.AMBIGUOUS;
        }

        return FailureClass.PERMANENT_BUSINESS;
    }

    public FailureClass classifyBankFailure(BankDispatchResult result) {
        String code = normalize(result == null ? null : result.getErrorCode());
        String message = normalize(result == null ? null : result.getErrorMessage());

        if (containsAny(code, message, "AML", "CFT", "SANCTION", "COMPLIANCE", "BLACKLIST")) {
            return FailureClass.PERMANENT_COMPLIANCE;
        }

        if (containsAny(code, message, "TIMEOUT", "TEMPORARY", "UNAVAILABLE", "RETRY", "CONNECTION")) {
            return FailureClass.TRANSIENT;
        }

        if (containsAny(code, message, "PACS002-UNKNOWN", "PACS002-NULL", "PACS002-001", "UNKNOWN", "AMBIGUOUS")) {
            return FailureClass.AMBIGUOUS;
        }

        return FailureClass.PERMANENT_BUSINESS;
    }

    public boolean shouldRetry(FailureClass failureClass, int attemptNo, int maxRetry) {
        return failureClass != null
                && failureClass.shouldRetry()
                && attemptNo < maxRetry;
    }

    public boolean shouldRejectTransfer(FailureClass failureClass, boolean willRetry) {
        return !willRetry && failureClass != null && failureClass != FailureClass.AMBIGUOUS;
    }

    private boolean containsAny(String code, String message, String... tokens) {
        for (String token : tokens) {
            if ((StringUtils.hasText(code) && code.contains(token))
                    || (StringUtils.hasText(message) && message.contains(token))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : "";
    }
}
