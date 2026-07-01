package com.example.switching.iso.mapper;

import org.springframework.stereotype.Component;

@Component
public class Pacs002XmlBuilder {

    private final LmpsMessageSupport lmps;

    public Pacs002XmlBuilder(LmpsMessageSupport lmps) {
        this.lmps = lmps;
    }

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
                "ACTC",
                null,
                null
        );
    }

    public String buildAcceptedResponse(
            String originalMessageId,
            String originalEndToEndId,
            String transferRef,
            String sourceBank,
            String destinationBank
    ) {
        return buildResponse(
                "PACS002-" + transferRef,
                originalMessageId,
                originalEndToEndId,
                transferRef,
                "ACTC",
                null,
                null,
                destinationBank,
                sourceBank
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

    public String buildRejectedResponse(
            String originalMessageId,
            String originalEndToEndId,
            String transferRef,
            String reasonCode,
            String reasonMessage,
            String sourceBank,
            String destinationBank
    ) {
        return buildResponse(
                "PACS002-" + transferRef,
                originalMessageId,
                originalEndToEndId,
                transferRef,
                "RJCT",
                reasonCode,
                reasonMessage,
                destinationBank,
                sourceBank
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
        return buildResponse(
                responseMessageId,
                originalMessageId,
                originalEndToEndId,
                transferRef,
                transactionStatus,
                reasonCode,
                reasonMessage,
                "LMPS",
                "SWITCH");
    }

    public String buildResponse(
            String responseMessageId,
            String originalMessageId,
            String originalEndToEndId,
            String transferRef,
            String transactionStatus,
            String reasonCode,
            String reasonMessage,
            String fromBank,
            String toBank
    ) {
        String createdAt = lmps.utcTimestamp();
        String lmpsMessageId = lmps.msgId(fromBank, responseMessageId);

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Message xmlns=\"").append(LmpsMessageSupport.APAC_NAMESPACE).append("\" ")
                .append("xmlns:head=\"").append(LmpsMessageSupport.HEAD_NAMESPACE).append("\" ")
                .append("xmlns:ps=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.002.10\">\n");
        xml.append(lmps.appHeader(
                fromBank,
                toBank,
                lmps.bizMsgIdr(transferRef),
                LmpsMessageSupport.PACS_002_MSG_DEF_IDR,
                createdAt));
        xml.append("  <PaymentStatusReport>\n");
        xml.append("    <FIToFIPmtStsRpt>\n");

        xml.append("      <GrpHdr>\n");
        xml.append("        <MsgId>").append(escape(lmpsMessageId)).append("</MsgId>\n");
        xml.append("        <CreDtTm>").append(escape(createdAt)).append("</CreDtTm>\n");
        xml.append("      </GrpHdr>\n");

        xml.append("      <OrgnlGrpInfAndSts>\n");
        xml.append("        <OrgnlMsgId>").append(escape(originalMessageId)).append("</OrgnlMsgId>\n");
        xml.append("        <OrgnlMsgNmId>pacs.008.002.08</OrgnlMsgNmId>\n");
        xml.append("        <OrgnlNbOfTxs>1</OrgnlNbOfTxs>\n");
        xml.append("      </OrgnlGrpInfAndSts>\n");

        xml.append("      <TxInfAndSts>\n");
        xml.append("        <OrgnlInstrId>").append(escape(transferRef)).append("</OrgnlInstrId>\n");
        xml.append("        <OrgnlEndToEndId>").append(escape(originalEndToEndId)).append("</OrgnlEndToEndId>\n");
        xml.append("        <OrgnlTxId>").append(escape(transferRef)).append("</OrgnlTxId>\n");
        xml.append("        <TxSts>").append(escape(transactionStatus)).append("</TxSts>\n");
        if (!"RJCT".equalsIgnoreCase(transactionStatus)) {
            xml.append("        <AccptncDtTm>").append(escape(createdAt)).append("</AccptncDtTm>\n");
        }
        xml.append("        <InstgAgt>\n");
        xml.append("          <FinInstnId><ClrSysMmbId><MmbId>")
                .append(escape(lmps.memberId(toBank)))
                .append("</MmbId></ClrSysMmbId></FinInstnId>\n");
        xml.append("        </InstgAgt>\n");
        xml.append("        <InstdAgt>\n");
        xml.append("          <FinInstnId><ClrSysMmbId><MmbId>")
                .append(escape(lmps.memberId(fromBank)))
                .append("</MmbId></ClrSysMmbId></FinInstnId>\n");
        xml.append("        </InstdAgt>\n");

        if ("RJCT".equalsIgnoreCase(transactionStatus)) {
            xml.append("        <StsRsnInf>\n");
            xml.append("          <Rsn>\n");
            xml.append("            <Cd>").append(escape(nullToDefault(reasonCode, "MS03"))).append("</Cd>\n");
            xml.append("          </Rsn>\n");

            if (reasonMessage != null && !reasonMessage.isBlank()) {
                xml.append("          <AddtlInf>").append(escape(reasonMessage)).append("</AddtlInf>\n");
            }

            xml.append("        </StsRsnInf>\n");
        }

        xml.append("      </TxInfAndSts>\n");

        xml.append("      <SplmtryData>\n");
        xml.append("        <Envlp>\n");
        xml.append("          <AdditionalData>\n");
        xml.append("            <ResDt>")
                .append("RJCT".equalsIgnoreCase(transactionStatus) ? "REJECTED" : "OK")
                .append("</ResDt>\n");
        xml.append("          </AdditionalData>\n");
        xml.append("        </Envlp>\n");
        xml.append("      </SplmtryData>\n");
        xml.append("    </FIToFIPmtStsRpt>\n");
        xml.append("  </PaymentStatusReport>\n");
        xml.append("</Message>\n");

        return xml.toString();
    }

    private String nullToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String escape(String value) {
        return lmps.xml(value);
    }
}
