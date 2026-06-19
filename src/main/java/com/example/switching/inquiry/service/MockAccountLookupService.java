package com.example.switching.inquiry.service;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Mock account-lookup used in development and test environments.
 *
 * <p>Accepts any non-blank account number as valid and returns a fixed display name.
 * Replace with a real implementation that calls the destination bank's name-inquiry
 * API before going to production.
 */
@Service
@Profile("!prod")
public class MockAccountLookupService implements AccountLookupService {

    @Override
    public AccountLookupResult lookup(String destinationBank, String accountNumber) {
        if (!StringUtils.hasText(accountNumber)) {
            return AccountLookupResult.notFound("creditorAccount is empty");
        }
        return AccountLookupResult.found("MOCK RECEIVER ACCOUNT");
    }
}
