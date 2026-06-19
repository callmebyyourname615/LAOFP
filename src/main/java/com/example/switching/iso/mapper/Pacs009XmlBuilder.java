package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.example.switching.settlement.entity.SettlementInstructionEntity;

@Component
public class Pacs009XmlBuilder {

    public String build(SettlementInstructionEntity instruction, String messageId) {
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String settlementDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
                  <FICdtTrf>
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
                      <IntrBkSttlmDt>%s</IntrBkSttlmDt>
                      <DbtrAgt>
                        <FinInstnId>
                          <ClrSysMmbId>
                            <MmbId>%s</MmbId>
                          </ClrSysMmbId>
                        </FinInstnId>
                      </DbtrAgt>
                      <CdtrAgt>
                        <FinInstnId>
                          <ClrSysMmbId>
                            <MmbId>%s</MmbId>
                          </ClrSysMmbId>
                        </FinInstnId>
                      </CdtrAgt>
                      <RmtInf>
                        <Ustrd>Settlement instruction %s</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                  </FICdtTrf>
                </Document>
                """.formatted(
                xml(messageId),
                xml(createdAt),
                xml(instruction.getInstructionRef()),
                xml(instruction.getInstructionRef()),
                xml(messageId),
                xml(instruction.getCurrency()),
                formatAmount(instruction.getNetAmount()),
                xml(settlementDate),
                xml(instruction.getDebtorPspId()),
                xml(instruction.getCreditorPspId()),
                xml(instruction.getInstructionRef())
        );
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
