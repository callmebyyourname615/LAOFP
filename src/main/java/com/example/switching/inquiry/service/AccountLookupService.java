package com.example.switching.inquiry.service;

/**
 * Abstraction for verifying that a creditor account exists at the destination bank.
 *
 * <p>The mock implementation accepts any non-blank account number and returns a
 * fixed account name. A production implementation would call the destination bank's
 * account-verification API (e.g. PromptPay proxy lookup, BAHTNET name inquiry).
 */
public interface AccountLookupService {

    /**
     * Verify that {@code accountNumber} exists at {@code destinationBank}.
     *
     * @param destinationBank normalised bank code of the receiving institution
     * @param accountNumber   the creditor account to verify
     * @return lookup result; never null
     */
    AccountLookupResult lookup(String destinationBank, String accountNumber);

    record AccountLookupResult(boolean found, String accountName, String errorReason) {

        public static AccountLookupResult found(String accountName) {
            return new AccountLookupResult(true, accountName, null);
        }

        public static AccountLookupResult notFound(String reason) {
            return new AccountLookupResult(false, null, reason);
        }
    }
}
