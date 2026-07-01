package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.example.switching.outbox.dto.StatusEnquiryResult;

@Component
public class Camt006XmlBuilder {

    private final LmpsMessageSupport lmps;

    public Camt006XmlBuilder(LmpsMessageSupport lmps) {
        this.lmps = lmps;
    }

    public String build(
            String transferRef,
            String endToEndId,
            String sourceBank,
            String destinationBank,
            BigDecimal amount,
            String currency,
            StatusEnquiryResult result
    ) {
        String createdAt = lmps.utcTimestamp();
        String responseMessageId = lmps.msgId(destinationBank, "CAMT006-" + transferRef);
        String status = statusReason(result);
        String responseCode = responseCode(result);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Message xmlns="%s"
                         xmlns:head="%s"
                         xmlns:rx="urn:iso:std:iso:20022:tech:xsd:camt.006.001.08">
                %s
                  <RtrTran>
                    <RtrTx>
                      <MsgHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                      </MsgHdr>
                      <RptOrErr>
                        <BizRpt>
                          <TxRpt>
                            <PmtId>
                              <TxId>%s</TxId>
                            </PmtId>
                            <TxOrErr>
                %s
                            </TxOrErr>
                          </TxRpt>
                        </BizRpt>
                      </RptOrErr>
                    </RtrTx>
                  </RtrTran>
                </Message>
                """.formatted(
                LmpsMessageSupport.APAC_NAMESPACE,
                LmpsMessageSupport.HEAD_NAMESPACE,
                lmps.appHeader(
                        destinationBank,
                        sourceBank,
                        lmps.bizMsgIdr(transferRef),
                        LmpsMessageSupport.CAMT_006_MSG_DEF_IDR,
                        createdAt),
                xml(responseMessageId),
                xml(createdAt),
                xml(transferRef),
                result.rejectedOrNotFound()
                        ? businessError(responseCode, result.responseMessage())
                        : transactionReport(transferRef, endToEndId, amount, currency, status, responseCode, createdAt));
    }

    private String transactionReport(
            String transferRef,
            String endToEndId,
            BigDecimal amount,
            String currency,
            String status,
            String responseCode,
            String createdAt
    ) {
        return """
                              <Tx>
                                <Pmt>
                                  <MsgId>%s</MsgId>
                                  <Sts>
                                    <Cd>
                                      <Prtry>%s</Prtry>
                                    </Cd>
                                    <DtTm>
                                      <DtTm>%s</DtTm>
                                    </DtTm>
                                    <Rsn>
                                      <Prtry>%s</Prtry>
                                    </Rsn>
                                  </Sts>
                                  <IntrBkSttlmAmt>
                                    <AmtWthCcy Ccy="%s">%s</AmtWthCcy>
                                  </IntrBkSttlmAmt>
                                  <TxId>%s</TxId>
                                  <IntrBkSttlmDt>%s</IntrBkSttlmDt>
                                  <EndToEndId>%s</EndToEndId>
                                </Pmt>
                              </Tx>
                """.formatted(
                xml(lmps.msgId("LMPS", transferRef)),
                xml(responseCode),
                xml(createdAt),
                xml(status),
                xml(hasText(currency) ? currency : "LAK"),
                formatAmount(amount),
                xml(transferRef),
                xml(lmps.settlementDate()),
                xml(endToEndId));
    }

    private String businessError(String errorCode, String description) {
        return """
                              <BizErr>
                                <Err>
                                  <Prtry>%s</Prtry>
                                </Err>
                                <Desc>%s</Desc>
                              </BizErr>
                """.formatted(
                xml(hasText(errorCode) ? errorCode : "PE01"),
                xml(hasText(description) ? description : "Payment not found"));
    }

    private String responseCode(StatusEnquiryResult result) {
        if (result == null || result.responseCode() == null || result.responseCode().isBlank()) {
            return "0000";
        }
        if (result.status() == StatusEnquiryResult.Status.ACCEPTED
                && "00".equals(result.responseCode())) {
            return "0000";
        }
        return result.responseCode();
    }

    private String statusReason(StatusEnquiryResult result) {
        if (result == null || result.status() == null) {
            return "UNKNOWN";
        }
        return switch (result.status()) {
            case ACCEPTED -> "ACTC";
            case PROCESSING -> "PDNG";
            case REJECTED -> "RJCT";
            case NOT_FOUND -> "RJCT";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String xml(String value) {
        return lmps.xml(value);
    }
}
