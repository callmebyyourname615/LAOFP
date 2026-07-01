package com.example.switching.iso.mapper;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LmpsMessageSupport {

    public static final String APAC_NAMESPACE = "urn:apac";
    public static final String HEAD_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:head.001.001.01";
    public static final String PACS_008_MSG_DEF_IDR = "pacs.008.002.08";
    public static final String PACS_002_MSG_DEF_IDR = "pacs.002.002.10";
    public static final String CAMT_005_MSG_DEF_IDR = "camt.005.001.08";
    public static final String CAMT_006_MSG_DEF_IDR = "camt.006.001.08";

    private static final DateTimeFormatter UTC_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final DateTimeFormatter BUSINESS_DATE =
            DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BIZ_MESSAGE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter MESSAGE_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    public String utcTimestamp() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(UTC_TIMESTAMP);
    }

    public String settlementDate() {
        return LocalDate.now(ZoneOffset.UTC).format(BUSINESS_DATE);
    }

    public String bizMsgIdr(String seed) {
        return "B" + OffsetDateTime.now(ZoneOffset.UTC).format(BIZ_MESSAGE_TIME) + sixteenDigits(seed);
    }

    public String msgId(String bankCode, String seed) {
        return "M" + OffsetDateTime.now(ZoneOffset.UTC).format(MESSAGE_TIME)
                + memberPrefix(bankCode) + sixteenDigits(seed);
    }

    public String instrId(String bankCode, String seed) {
        return "I" + OffsetDateTime.now(ZoneOffset.UTC).format(MESSAGE_TIME)
                + memberPrefix(bankCode) + sixteenDigits(seed);
    }

    public String txId(String seed) {
        String normalizedSeed = normalizeSeed(seed);
        if (normalizedSeed.length() >= 35) {
            return normalizedSeed.substring(0, Math.min(normalizedSeed.length(), 100));
        }
        return "TX" + OffsetDateTime.now(ZoneOffset.UTC).format(BIZ_MESSAGE_TIME)
                + sixteenDigits(seed);
    }

    public String appHeader(
            String fromMemberId,
            String toMemberId,
            String bizMsgIdr,
            String msgDefIdr,
            String createdAt
    ) {
        return """
                    <AppHdr>
                      <Fr>
                        <FIId>
                          <FinInstnId>
                            <ClrSysMmbId>
                              <MmbId>%s</MmbId>
                            </ClrSysMmbId>
                          </FinInstnId>
                        </FIId>
                      </Fr>
                      <To>
                        <FIId>
                          <FinInstnId>
                            <ClrSysMmbId>
                              <MmbId>%s</MmbId>
                            </ClrSysMmbId>
                          </FinInstnId>
                        </FIId>
                      </To>
                      <BizMsgIdr>%s</BizMsgIdr>
                      <MsgDefIdr>%s</MsgDefIdr>
                      <CreDt>%s</CreDt>
                    </AppHdr>
                """.formatted(
                xml(memberId(fromMemberId)),
                xml(memberId(toMemberId)),
                xml(bizMsgIdr),
                xml(msgDefIdr),
                xml(createdAt));
    }

    public String memberId(String bankCode) {
        String normalized = normalizeSeed(bankCode).toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return "UNKNOWN";
        }
        if (normalized.length() >= 8) {
            return normalized.substring(0, Math.min(normalized.length(), 12));
        }
        return normalized;
    }

    public String memberPrefix(String bankCode) {
        String normalized = normalizeSeed(bankCode).toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return "LMPS";
        }
        if (normalized.length() >= 4) {
            return normalized.substring(0, 4);
        }
        return (normalized + "XXXX").substring(0, 4);
    }

    public String xml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sixteenDigits(String seed) {
        String digits = seed == null ? "" : seed.replaceAll("\\D", "");
        if (digits.length() >= 16) {
            return digits.substring(digits.length() - 16);
        }
        long random = Math.abs(RANDOM.nextLong());
        String padded = (digits + String.format("%019d", random));
        return padded.substring(0, 16);
    }

    private String normalizeSeed(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "");
    }
}
