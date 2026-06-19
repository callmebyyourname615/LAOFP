package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.example.switching.transfer.entity.TransferEntity;

@Component
public class Pacs008XmlBuilder {

    public String build(TransferEntity transfer, String messageId, String endToEndId) {
        String amount = formatAmount(transfer.getAmount());
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
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
                      <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                      <DbtrAgt>
                        <FinInstnId>
                          <BICFI>%s</BICFI>
                        </FinInstnId>
                      </DbtrAgt>
                      <DbtrAcct>
                        <Id>
                          <Othr>
                            <Id>%s</Id>
                          </Othr>
                        </Id>
                      </DbtrAcct>
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
                      <RmtInf>
                        <Ustrd>%s</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """.formatted(
                xml(messageId),
                xml(createdAt),
                xml(transfer.getTransferRef()),
                xml(endToEndId),
                xml(transfer.getTransferRef()),
                xml(transfer.getCurrency()),
                amount,
                xml(transfer.getSourceBank()),
                xml(transfer.getDebtorAccount()),
                xml(transfer.getDestinationBank()),
                xml(transfer.getDestinationAccountName()),
                xml(transfer.getCreditorAccount()),
                xml(transfer.getReference())
        );
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String xml(String value) {
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