package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public class Camt005XmlBuilder {

    private final LmpsMessageSupport lmps;

    public Camt005XmlBuilder(LmpsMessageSupport lmps) {
        this.lmps = lmps;
    }

    public String build(
            String transferRef,
            String originalMessageId,
            String originalEndToEndId,
            String sourceBank,
            String destinationBank,
            BigDecimal amount,
            String currency,
            String debtorAccount,
            String creditorAccount
    ) {
        String createdAt = lmps.utcTimestamp();
        String queryId = lmps.txId(transferRef);
        String amountText = formatAmount(amount);
        String resolvedCurrency = hasText(currency) ? currency : "LAK";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Message xmlns="%s"
                         xmlns:head="%s"
                         xmlns:gt="urn:iso:std:iso:20022:tech:xsd:camt.005.001.08">
                %s
                  <GetTran>
                    <GetTx>
                      <MsgHdr>
                        <CreDtTm>%s</CreDtTm>
                        <ReqTp>
                          <Prtry>
                            <Id>%s</Id>
                          </Prtry>
                        </ReqTp>
                      </MsgHdr>
                      <TxQryDef>
                        <TxCrit>
                          <NewCrit>
                            <SchCrit>
                              <PmtSch>
                                <MsgId>%s</MsgId>
                                <ReqdExctnDt>
                                  <DtTmSch>
                                    <DtTmRg>
                                      <FrDtTm>%s</FrDtTm>
                                      <ToDtTm>%s</ToDtTm>
                                    </DtTmRg>
                                  </DtTmSch>
                                </ReqdExctnDt>
                                <PmtId>
                                  <TxId>%s</TxId>
                                </PmtId>
                                <IntrBkSttlmAmt>
                                  <CcyAndAmtRg>
                                    <Amt>
                                      <EQAmt>%s</EQAmt>
                                    </Amt>
                                    <Ccy>%s</Ccy>
                                  </CcyAndAmtRg>
                                </IntrBkSttlmAmt>
                                <EndToEndId>%s</EndToEndId>
                                <Pties>
                                  <InstgAgt>
                                    <FinInstnId>
                                      <ClrSysMmbId>
                                        <MmbId>%s</MmbId>
                                      </ClrSysMmbId>
                                    </FinInstnId>
                                  </InstgAgt>
                                  <InstdAgt>
                                    <FinInstnId>
                                      <ClrSysMmbId>
                                        <MmbId>%s</MmbId>
                                      </ClrSysMmbId>
                                    </FinInstnId>
                                  </InstdAgt>
                                  <Dbtr>
                                    <Pty>
                                      <Id>
                                        <PrvtId>
                                          <Othr>
                                            <Id>%s</Id>
                                          </Othr>
                                        </PrvtId>
                                      </Id>
                                    </Pty>
                                  </Dbtr>
                                  <Cdtr>
                                    <Pty>
                                      <Id>
                                        <PrvtId>
                                          <Othr>
                                            <Id>%s</Id>
                                          </Othr>
                                        </PrvtId>
                                      </Id>
                                    </Pty>
                                  </Cdtr>
                                </Pties>
                              </PmtSch>
                            </SchCrit>
                          </NewCrit>
                        </TxCrit>
                      </TxQryDef>
                    </GetTx>
                  </GetTran>
                </Message>
                """.formatted(
                LmpsMessageSupport.APAC_NAMESPACE,
                LmpsMessageSupport.HEAD_NAMESPACE,
                lmps.appHeader(
                        sourceBank,
                        destinationBank,
                        lmps.bizMsgIdr(transferRef),
                        LmpsMessageSupport.CAMT_005_MSG_DEF_IDR,
                        createdAt),
                xml(createdAt),
                xml(queryId),
                xml(originalMessageId),
                xml(OffsetDateTime.now(ZoneOffset.UTC).minusHours(24).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))),
                xml(createdAt),
                xml(transferRef),
                amountText,
                xml(resolvedCurrency),
                xml(originalEndToEndId),
                xml(lmps.memberId(sourceBank)),
                xml(lmps.memberId(destinationBank)),
                xml(debtorAccount),
                xml(creditorAccount));
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
