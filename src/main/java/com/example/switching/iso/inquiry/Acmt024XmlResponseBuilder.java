package com.example.switching.iso.inquiry;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public class Acmt024XmlResponseBuilder {

    private static final String NS = "urn:iso:std:iso:20022:tech:xsd:acmt.024.001.03";

    public String accepted(Acmt023InquiryRequest request, String inquiryRef) {
        return build(request, inquiryRef, "MTCH", null, null);
    }

    public String rejected(Acmt023InquiryRequest request, String reasonCode, String reasonMessage) {
        return build(request, null, "NMTC", reasonCode, reasonMessage);
    }

    private String build(
            Acmt023InquiryRequest request,
            String inquiryRef,
            String status,
            String reasonCode,
            String reasonMessage
    ) {
        String messageId = "ACMT024-" + safe(request.getMessageId()) + "-" + System.currentTimeMillis();

        String reasonBlock = "";
        if (reasonCode != null || reasonMessage != null) {
            reasonBlock = """
                    <Rsn>
                      <Cd>%s</Cd>
                      <AddtlInf>%s</AddtlInf>
                    </Rsn>
                    """.formatted(escape(reasonCode), escape(reasonMessage));
        }

        String inquiryBlock = "";
        if (inquiryRef != null) {
            inquiryBlock = """
                    <SplmtryData>
                      <PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm>
                      <Envlp>
                        <InquiryRef>%s</InquiryRef>
                      </Envlp>
                    </SplmtryData>
                    """.formatted(escape(inquiryRef));
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="%s">
                  <IdVrfctnRpt>
                    <Assgnmt>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                    </Assgnmt>
                    <OrgnlAssgnmt>
                      <MsgId>%s</MsgId>
                    </OrgnlAssgnmt>
                    <Rpt>
                      <OrgnlId>%s</OrgnlId>
                      <Vrfctn>%s</Vrfctn>
                      %s
                    </Rpt>
                    %s
                  </IdVrfctnRpt>
                </Document>
                """.formatted(
                NS,
                escape(messageId),
                OffsetDateTime.now(ZoneOffset.UTC),
                escape(request.getMessageId()),
                escape(firstNonBlank(request.getInstructionId(), request.getEndToEndId(), request.getMessageId())),
                escape(status),
                reasonBlock,
                inquiryBlock
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim();
    }

    private String escape(String value) {
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
}