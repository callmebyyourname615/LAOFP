package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Builds ISO 20022 camt.056.001.08 FIToFIPaymentCancellationRequest XML.
 *
 * <p>Sent by the switching centre back to the originating bank when an outbound
 * transfer cannot be completed and the funds must be returned.
 */
@Component
public class Camt056XmlBuilder {

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Build a CAMT.056 cancellation request.
     *
     * @param cancellationMsgId unique ID for this cancellation message
     * @param originalMsgId     message ID of the original PACS.008 that is being cancelled
     * @param originalEndToEndId end-to-end ID from the original PACS.008
     * @param originalTxnId     transaction (transfer) reference from the original payment
     * @param amount            original instructed amount
     * @param currency          original currency code
     * @param reasonCode        ISO 20022 cancellation reason code (e.g. FOCR, DUPL, AM09, CUST)
     * @param additionalInfo    optional free-text reason (may be null)
     * @return well-formed camt.056 XML string
     */
    public String build(
            String cancellationMsgId,
            String originalMsgId,
            String originalEndToEndId,
            String originalTxnId,
            BigDecimal amount,
            String currency,
            String reasonCode,
            String additionalInfo) {

        String createdAt = LocalDateTime.now().format(ISO_DT);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.056.001.08\">\n");
        xml.append("  <FIToFIPmtCxlReq>\n");

        xml.append("    <Assgnmt>\n");
        xml.append("      <MsgId>").append(esc(cancellationMsgId)).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(createdAt).append("</CreDtTm>\n");
        xml.append("    </Assgnmt>\n");

        xml.append("    <Undrlyg>\n");
        xml.append("      <OrgnlGrpInfAndCxl>\n");
        xml.append("        <OrgnlMsgId>").append(esc(originalMsgId)).append("</OrgnlMsgId>\n");
        xml.append("        <OrgnlMsgNmId>pacs.008.001.08</OrgnlMsgNmId>\n");
        xml.append("      </OrgnlGrpInfAndCxl>\n");

        xml.append("      <TxInf>\n");
        xml.append("        <CxlId>").append(esc(cancellationMsgId)).append("</CxlId>\n");
        xml.append("        <OrgnlEndToEndId>").append(esc(orEmpty(originalEndToEndId))).append("</OrgnlEndToEndId>\n");
        xml.append("        <OrgnlTxId>").append(esc(originalTxnId)).append("</OrgnlTxId>\n");

        if (amount != null && currency != null) {
            xml.append("        <OrgnlIntrBkSttlmAmt Ccy=\"").append(esc(currency)).append("\">")
               .append(amount.toPlainString())
               .append("</OrgnlIntrBkSttlmAmt>\n");
        }

        xml.append("        <CxlRsnInf>\n");
        xml.append("          <Rsn>\n");
        xml.append("            <Cd>").append(esc(orDefault(reasonCode, "FOCR"))).append("</Cd>\n");
        xml.append("          </Rsn>\n");
        if (additionalInfo != null && !additionalInfo.isBlank()) {
            xml.append("          <AddtlInf>").append(esc(additionalInfo)).append("</AddtlInf>\n");
        }
        xml.append("        </CxlRsnInf>\n");

        xml.append("      </TxInf>\n");
        xml.append("    </Undrlyg>\n");

        xml.append("  </FIToFIPmtCxlReq>\n");
        xml.append("</Document>\n");

        return xml.toString();
    }

    private String esc(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private String orDefault(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
