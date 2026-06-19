package com.example.switching.iso.inbound;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

@Component
public class Pacs002XmlResponseBuilder {

    private static final String PACS_002_NAMESPACE =
            "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12";

    public String accepted(Pacs008InboundRequest request) {
        return build(request, "ACTC", "ACTC", null, null, null);
    }

    public String accepted(Pacs008InboundRequest request, String transferRef) {
        return build(request, "ACTC", "ACTC", null, null, transferRef);
    }

    public String rejected(Pacs008InboundRequest request, String reasonCode, String reasonDescription) {
        return build(request, "RJCT", "RJCT", reasonCode, reasonDescription, null);
    }

    public String rejectedWithoutOriginalMessage(String reasonCode, String reasonDescription) {
        return build(null, "RJCT", "RJCT", reasonCode, reasonDescription, null);
    }

    private String build(
            Pacs008InboundRequest request,
            String groupStatus,
            String transactionStatus,
            String reasonCode,
            String reasonDescription,
            String transferRef
    ) {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String originalMessageId = valueOrUnknown(request == null ? null : request.getMessageId());
        String originalInstructionId = valueOrUnknown(request == null ? null : request.getInstructionId());
        String originalEndToEndId = valueOrUnknown(request == null ? null : request.getEndToEndId());

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Document xmlns=\"").append(PACS_002_NAMESPACE).append("\">");
        xml.append("<FIToFIPmtStsRpt>");

        xml.append("<GrpHdr>");
        xml.append("<MsgId>").append(escape("PACS002-" + originalMessageId + "-" + System.currentTimeMillis())).append("</MsgId>");
        xml.append("<CreDtTm>").append(escape(now)).append("</CreDtTm>");
        xml.append("</GrpHdr>");

        xml.append("<OrgnlGrpInfAndSts>");
        xml.append("<OrgnlMsgId>").append(escape(originalMessageId)).append("</OrgnlMsgId>");
        xml.append("<OrgnlMsgNmId>pacs.008.001.12</OrgnlMsgNmId>");
        xml.append("<GrpSts>").append(escape(groupStatus)).append("</GrpSts>");
        xml.append("</OrgnlGrpInfAndSts>");

        xml.append("<TxInfAndSts>");
        xml.append("<OrgnlInstrId>").append(escape(originalInstructionId)).append("</OrgnlInstrId>");
        xml.append("<OrgnlEndToEndId>").append(escape(originalEndToEndId)).append("</OrgnlEndToEndId>");
        xml.append("<TxSts>").append(escape(transactionStatus)).append("</TxSts>");

        if (transferRef != null && !transferRef.isBlank()) {
            xml.append("<AcctSvcrRef>").append(escape(transferRef)).append("</AcctSvcrRef>");
        }

        if ("RJCT".equals(transactionStatus)) {
            xml.append("<StsRsnInf>");
            xml.append("<Rsn>");
            xml.append("<Cd>").append(escape(valueOrDefault(reasonCode, "NARR"))).append("</Cd>");
            xml.append("</Rsn>");
            xml.append("<AddtlInf>").append(escape(valueOrDefault(reasonDescription, "Rejected by switching validation"))).append("</AddtlInf>");
            xml.append("</StsRsnInf>");
        }

        xml.append("</TxInfAndSts>");

        xml.append("</FIToFIPmtStsRpt>");
        xml.append("</Document>");

        return xml.toString();
    }

    private String valueOrUnknown(String value) {
        return valueOrDefault(value, "UNKNOWN");
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
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