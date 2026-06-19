package com.example.switching.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaskingUtil {

    private MaskingUtil() {}

    /**
     * Matches the leaf <Id> value inside <Othr> inside <DbtrAcct> or <CdtrAcct>.
     * Used to mask account numbers when ISO XML payloads appear in logs.
     */
    private static final Pattern XML_ACCT_PATTERN = Pattern.compile(
            "(<(?:Dbtr|Cdtr)Acct>(?:[^<]|<(?!/?(?:Dbtr|Cdtr)Acct))*?<Othr>(?:[^<]|<(?!/?Othr))*?<Id>)"
                    + "([^<]+)"
                    + "(</Id>)",
            Pattern.DOTALL
    );

    private static final Pattern JSON_ACCOUNT_FIELD_PATTERN = Pattern.compile(
            "(\"(?:debtorAccount|creditorAccount|sourceAccount|destinationAccount|sourceAccountNo|destinationAccountNo|source_account_no|destination_account_no|debtor_account_no|creditor_account_no)\"\\s*:\\s*\")"
                    + "([^\"]+)"
                    + "(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Masks account numbers inside ISO 20022 XML <DbtrAcct> and <CdtrAcct> elements.
     * Safe to call on any String — returns null if input is null, original value if no match.
     * Intended for debug/trace logging only; DB storage uses full account numbers.
     */
    public static String maskXmlAccounts(String xml) {
        if (xml == null) return null;
        return XML_ACCT_PATTERN.matcher(xml).replaceAll(m ->
                Matcher.quoteReplacement(
                        m.group(1) + maskAccount(m.group(2).trim()) + m.group(3)));
    }

    /**
     * Masks account fields inside JSON-like audit payloads and ISO XML payloads.
     */
    public static String maskAccountFieldsInText(String value) {
        if (value == null) return null;
        String xmlMasked = maskXmlAccounts(value);
        return JSON_ACCOUNT_FIELD_PATTERN.matcher(xmlMasked).replaceAll(m ->
                Matcher.quoteReplacement(
                        m.group(1) + maskAccount(m.group(2).trim()) + m.group(3)));
    }

    /**
     * Masks an account number, showing only the last 4 digits.
     * e.g. "1234567890" → "******7890"
     */
    public static String maskAccount(String accountNo) {
        if (accountNo == null) return null;
        if (accountNo.length() <= 4) return "****";
        return "*".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
    }

    /**
     * Masks a generic sensitive value, showing only the last N visible chars.
     */
    public static String maskSensitive(String value, int visibleSuffix) {
        if (value == null) return null;
        if (value.length() <= visibleSuffix) return "*".repeat(value.length());
        return "*".repeat(value.length() - visibleSuffix) + value.substring(value.length() - visibleSuffix);
    }
}
