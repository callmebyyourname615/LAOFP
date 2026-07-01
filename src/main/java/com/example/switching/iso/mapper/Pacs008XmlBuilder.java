package com.example.switching.iso.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.example.switching.transfer.entity.TransferEntity;

@Component
public class Pacs008XmlBuilder {

    private final LmpsMessageSupport lmps;

    public Pacs008XmlBuilder(LmpsMessageSupport lmps) {
        this.lmps = lmps;
    }

    public String build(TransferEntity transfer, String messageId, String endToEndId) {
        String amount = formatAmount(transfer.getAmount());
        String createdAt = lmps.utcTimestamp();
        String lmpsMessageId = lmps.msgId(transfer.getSourceBank(), messageId);
        String instructionId = lmps.instrId(transfer.getSourceBank(), transfer.getTransferRef());
        String transactionId = lmps.txId(transfer.getTransferRef());

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Message xmlns="%s"
                         xmlns:ct="urn:iso:std:iso:20022:tech:xsd:pacs.008.002.08"
                         xmlns:head="%s">
                %s
                  <CreditTransfer>
                    <FIToFICstmrCdtTrf>
                      <GrpHdr>
                        <MsgId>%s</MsgId>
                        <CreDtTm>%s</CreDtTm>
                        <NbOfTxs>1</NbOfTxs>
                        <TtlIntrBkSttlmAmt Ccy="%s">%s</TtlIntrBkSttlmAmt>
                        <IntrBkSttlmDt>%s</IntrBkSttlmDt>
                        <SttlmInf>
                          <SttlmMtd>CLRG</SttlmMtd>
                        </SttlmInf>
                      </GrpHdr>
                      <CdtTrfTxInf>
                        <PmtId>
                          <InstrId>%s</InstrId>
                          <EndToEndId>%s</EndToEndId>
                          <TxId>%s</TxId>
                        </PmtId>
                        <PmtTpInf>
                          <SvcLvl>
                            <Cd>SDVA</Cd>
                          </SvcLvl>
                          <CtgyPurp>
                            <Prtry>P2PTransfer</Prtry>
                          </CtgyPurp>
                        </PmtTpInf>
                        <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                        <SttlmTmIndctn>
                          <DbtDtTm>%s</DbtDtTm>
                        </SttlmTmIndctn>
                        <ChrgBr>SLEV</ChrgBr>
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
                          <Nm>%s</Nm>
                        </Dbtr>
                        <DbtrAcct>
                          <Id>
                            <Othr>
                              <Id>%s</Id>
                            </Othr>
                          </Id>
                        </DbtrAcct>
                        <DbtrAgt>
                          <FinInstnId>
                            <BICFI>%s</BICFI>
                          </FinInstnId>
                        </DbtrAgt>
                        <CdtrAgt>
                          <FinInstnId>
                            <BICFI>%s</BICFI>
                          </FinInstnId>
                        </CdtrAgt>
                        <Cdtr>
                          <Nm>%s</Nm>
                        </Cdtr>
                        <CdtrAcct>
                          <Id>
                            <Othr>
                              <Id>%s</Id>
                            </Othr>
                          </Id>
                        </CdtrAcct>
                        <Purp>
                          <Cd>GDDS</Cd>
                        </Purp>
                        <RmtInf>
                          <Ustrd>%s</Ustrd>
                        </RmtInf>
                      </CdtTrfTxInf>
                      <SplmtryData>
                        <Envlp>
                          <AdditionalData>
                            <FrmUsr>%s</FrmUsr>
                            <FrmAccTyp>PERSONAL</FrmAccTyp>
                            <FromChannel>API</FromChannel>
                            <ToType>ACCOUNT</ToType>
                          </AdditionalData>
                        </Envlp>
                      </SplmtryData>
                    </FIToFICstmrCdtTrf>
                  </CreditTransfer>
                </Message>
                """.formatted(
                LmpsMessageSupport.APAC_NAMESPACE,
                LmpsMessageSupport.HEAD_NAMESPACE,
                lmps.appHeader(
                        transfer.getSourceBank(),
                        transfer.getDestinationBank(),
                        lmps.bizMsgIdr(transfer.getTransferRef()),
                        LmpsMessageSupport.PACS_008_MSG_DEF_IDR,
                        createdAt),
                xml(lmpsMessageId),
                xml(createdAt),
                xml(transfer.getCurrency()),
                amount,
                xml(lmps.settlementDate()),
                xml(instructionId),
                xml(endToEndId),
                xml(transactionId),
                xml(transfer.getCurrency()),
                amount,
                xml(createdAt),
                xml(transfer.getSourceBank()),
                xml(transfer.getDestinationBank()),
                xml(transfer.getDebtorAccount()),
                xml(transfer.getDebtorAccount()),
                xml(transfer.getSourceBank()),
                xml(transfer.getDestinationBank()),
                xml(transfer.getDestinationAccountName()),
                xml(transfer.getCreditorAccount()),
                xml(transfer.getReference()),
                xml(transfer.getDebtorAccount())
        );
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String xml(String value) {
        return lmps.xml(value);
    }
}
