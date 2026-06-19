package com.example.switching.iso.mapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

@Component
public class Pacs002XmlBuilder {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public String buildAcceptedResponse(
            String originalMessageId,
            String originalEndToEndId,
            String transferRef
    ) {
        return buildResponse(
                "PACS002-" + transferRef,
                originalMessageId,
                originalEndToEndId,
                transferRef,
                "ACSC",
                null,
                null
        );
    }

    public String buildRejectedResponse(
            String originalMessageId,
            String originalEndToEndId,
            String transferRef,
            String reasonCode,
            String reasonMessage
    ) {
        return buildResponse(
                "PACS002-" + transferRef,
                originalMessageId,
                originalEndToEndId,
                transferRef,
                "RJCT",
                reasonCode,
                reasonMessage
        );
    }

    public String buildResponse(
            String responseMessageId,
            String originalMessageId,
            String originalEndToEndId,
            String transferRef,
            String transactionStatus,
            String reasonCode,
            String reasonMessage
    ) {
        String createdAt = LocalDateTime.now().format(ISO_DATE_TIME);

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10\">\n");
        xml.append("  <FIToFIPmtStsRpt>\n");

        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(escape(responseMessageId)).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(escape(createdAt)).append("</CreDtTm>\n");
        xml.append("    </GrpHdr>\n");

        xml.append("    <OrgnlGrpInfAndSts>\n");
        xml.append("      <OrgnlMsgId>").append(escape(originalMessageId)).append("</OrgnlMsgId>\n");
        xml.append("      <OrgnlMsgNmId>pacs.008.001.08</OrgnlMsgNmId>\n");
        xml.append("    </OrgnlGrpInfAndSts>\n");

        xml.append("    <TxInfAndSts>\n");
        xml.append("      <OrgnlEndToEndId>").append(escape(originalEndToEndId)).append("</OrgnlEndToEndId>\n");
        xml.append("      <OrgnlTxId>").append(escape(transferRef)).append("</OrgnlTxId>\n");
        xml.append("      <TxSts>").append(escape(transactionStatus)).append("</TxSts>\n");

        if ("RJCT".equalsIgnoreCase(transactionStatus)) {
            xml.append("      <StsRsnInf>\n");
            xml.append("        <Rsn>\n");
            xml.append("          <Cd>").append(escape(nullToDefault(reasonCode, "MS03"))).append("</Cd>\n");
            xml.append("        </Rsn>\n");

            if (reasonMessage != null && !reasonMessage.isBlank()) {
                xml.append("        <AddtlInf>").append(escape(reasonMessage)).append("</AddtlInf>\n");
            }

            xml.append("      </StsRsnInf>\n");
        }

        xml.append("    </TxInfAndSts>\n");

        xml.append("  </FIToFIPmtStsRpt>\n");
        xml.append("</Document>\n");

        return xml.toString();
    }

    private String nullToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
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