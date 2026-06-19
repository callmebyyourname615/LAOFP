package com.example.switching.iso.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Builds ISO 20022 camt.054.001.08 BankToCustomerDebitCreditNotification XML.
 *
 * <p>Generated once per PSP per settled DNS cycle and stored in {@code settlement_reports}.
 * The notification summarises the PSP's gross debit, gross credit, and net position
 * resulting from multilateral netting of that cycle.
 *
 * <p>Structure per PSP:
 * <ul>
 *   <li>One {@code <Ntfctn>} block per PSP account.</li>
 *   <li>One DBIT entry for gross debit (total outgoing volume).</li>
 *   <li>One CRDT entry for gross credit (total incoming volume).</li>
 *   <li>One net-position summary entry: DBIT when net negative, CRDT when net positive.</li>
 * </ul>
 */
@Component
public class Camt054XmlBuilder {

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Build a camt.054 notification XML for a single PSP's settlement position.
     *
     * @param msgId       unique message identifier for this notification
     * @param cycleRef    settlement cycle reference (e.g. {@code SC-20260528-C2})
     * @param pspId       bank code of the PSP being notified
     * @param grossDebit  total outgoing amount settled in this cycle
     * @param grossCredit total incoming amount settled in this cycle
     * @param currency    ISO 4217 currency code (e.g. {@code LAK})
     * @param settledAt   timestamp when the cycle was settled
     * @return well-formed camt.054.001.08 XML string
     */
    public String build(
            String msgId,
            String cycleRef,
            String pspId,
            BigDecimal grossDebit,
            BigDecimal grossCredit,
            String currency,
            LocalDateTime settledAt) {

        String createdAt  = LocalDateTime.now().format(ISO_DT);
        String settledStr = settledAt != null ? settledAt.format(ISO_DT) : createdAt;
        String ccy        = currency != null ? esc(currency) : "LAK";

        BigDecimal net    = grossCredit.subtract(grossDebit);
        boolean netCredit = net.compareTo(BigDecimal.ZERO) >= 0;
        String netInd     = netCredit ? "CRDT" : "DBIT";
        BigDecimal netAbs = net.abs();

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
             + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.054.001.08\">\n"
             + "  <BkToCstmrDbtCdtNtfctn>\n"
             + "    <GrpHdr>\n"
             + "      <MsgId>" + esc(msgId) + "</MsgId>\n"
             + "      <CreDtTm>" + createdAt + "</CreDtTm>\n"
             + "      <MsgPgntn>\n"
             + "        <PgNb>1</PgNb>\n"
             + "        <LastPgInd>true</LastPgInd>\n"
             + "      </MsgPgntn>\n"
             + "      <AddtlInf>LaoFP DNS Settlement Cycle " + esc(cycleRef) + "</AddtlInf>\n"
             + "    </GrpHdr>\n"
             + "    <Ntfctn>\n"
             + "      <Id>" + esc(msgId) + "</Id>\n"
             + "      <CreDtTm>" + createdAt + "</CreDtTm>\n"
             + "      <Acct>\n"
             + "        <Id>\n"
             + "          <Othr>\n"
             + "            <Id>" + esc(pspId) + "</Id>\n"
             + "            <SchmeNm><Prtry>LAOFP_PSP_CODE</Prtry></SchmeNm>\n"
             + "          </Othr>\n"
             + "        </Id>\n"
             + "        <Ccy>" + ccy + "</Ccy>\n"
             + "      </Acct>\n"
             // DBIT entry: gross debit (outgoing from this PSP)
             + buildEntry("DBIT-" + cycleRef, fmt(grossDebit), ccy, "DBIT", settledStr,
                         "Gross debit settlement DNS cycle " + cycleRef)
             // CRDT entry: gross credit (incoming to this PSP)
             + buildEntry("CRDT-" + cycleRef, fmt(grossCredit), ccy, "CRDT", settledStr,
                         "Gross credit settlement DNS cycle " + cycleRef)
             // Net position entry
             + buildEntry("NET-" + cycleRef, fmt(netAbs), ccy, netInd, settledStr,
                         "Net position DNS cycle " + cycleRef + " (multilateral netting)")
             + "    </Ntfctn>\n"
             + "  </BkToCstmrDbtCdtNtfctn>\n"
             + "</Document>\n";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildEntry(String ref, String amount, String ccy,
                               String cdtDbtInd, String bookDtTm, String addtlInfo) {
        return "      <Ntry>\n"
             + "        <NtryRef>" + esc(ref) + "</NtryRef>\n"
             + "        <Amt Ccy=\"" + ccy + "\">" + amount + "</Amt>\n"
             + "        <CdtDbtInd>" + cdtDbtInd + "</CdtDbtInd>\n"
             + "        <Sts><Cd>BOOK</Cd></Sts>\n"
             + "        <BookgDt><DtTm>" + bookDtTm + "</DtTm></BookgDt>\n"
             + "        <AddtlNtryInf>" + esc(addtlInfo) + "</AddtlNtryInf>\n"
             + "      </Ntry>\n";
    }

    private String fmt(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
