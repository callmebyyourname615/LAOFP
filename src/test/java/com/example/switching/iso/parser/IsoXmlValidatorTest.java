package com.example.switching.iso.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.iso.dto.IsoXmlValidationResult;
import com.example.switching.iso.enums.IsoMessageType;

/**
 * Unit tests for IsoXmlValidator.
 *
 * Two-phase validation:
 *   Phase 1 — XSD structural validation (ISO 20022 simplified schemas)
 *   Phase 2 — field-level business validation (required-field checks)
 *
 * XSD errors are persisted to iso_validation_errors only when an isoMessageId is supplied.
 */
@ExtendWith(MockitoExtension.class)
class IsoXmlValidatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IsoXmlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IsoXmlValidator(jdbcTemplate);
        validator.loadSchemas(); // simulate @PostConstruct — loads XSDs from test classpath
    }

    // ── Empty / null payload ─────────────────────────────────────────────────

    @Test
    void emptyXml_returnsISO_VAL_001() {
        IsoXmlValidationResult result = validator.validate("", IsoMessageType.PACS_008);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-001", result.getErrorCode());
    }

    @Test
    void blankXml_returnsISO_VAL_001() {
        IsoXmlValidationResult result = validator.validate("   ", null);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-001", result.getErrorCode());
    }

    // ── Namespace / type detection ────────────────────────────────────────────

    @Test
    void unknownNamespace_returnsISO_VAL_002() {
        String xml = """
                <?xml version="1.0"?>
                <Document xmlns="urn:unknown:schema"><Unknown/></Document>
                """;

        IsoXmlValidationResult result = validator.validate(xml, null);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-002", result.getErrorCode());
        assertNull(result.getDetectedMessageType());
    }

    @Test
    void typeMismatch_pacs008XmlExpectedAsPacs002_returnsISO_VAL_003() {
        IsoXmlValidationResult result = validator.validate(validPacs008Xml(), IsoMessageType.PACS_002);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-003", result.getErrorCode());
        assertEquals("PACS_008", result.getDetectedMessageType());
    }

    // ── PACS.008 XSD validation ───────────────────────────────────────────────

    @Test
    void validPacs008_passesXsdAndFieldValidation() {
        IsoXmlValidationResult result = validator.validate(validPacs008Xml(), IsoMessageType.PACS_008);

        assertTrue(result.isValid());
        assertEquals("PACS_008", result.getDetectedMessageType());
        assertEquals("MSG-PACS008-001", result.getMessageId());
        assertEquals("E2E-001", result.getEndToEndId());
        assertNull(result.getTransactionStatus());
    }

    @Test
    void pacs008_autoDetectedWithoutExpectedType() {
        IsoXmlValidationResult result = validator.validate(validPacs008Xml(), null);

        assertTrue(result.isValid());
        assertEquals("PACS_008", result.getDetectedMessageType());
    }

    @Test
    void pacs008MissingNbOfTxs_failsXsdValidation() {
        // NbOfTxs is required in GroupHeader93 — XSD must reject it
        IsoXmlValidationResult result = validator.validate(pacs008MissingNbOfTxsXml(), IsoMessageType.PACS_008);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-XSD-001", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("XSD validation failed"));
    }

    @Test
    void pacs008XsdError_persistedToDbWhenIsoMessageIdGiven() {
        long isoMessageId = 42L;

        IsoXmlValidationResult result = validator.validate(
                pacs008MissingNbOfTxsXml(), IsoMessageType.PACS_008, isoMessageId);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-XSD-001", result.getErrorCode());
        // jdbcTemplate.update must have been called at least once with the isoMessageId
        verify(jdbcTemplate, atLeastOnce())
                .update(anyString(), eq(isoMessageId), any(), any(), any(), any(), any());
    }

    @Test
    void pacs008XsdError_notPersistedWhenIsoMessageIdNull() {
        // With null isoMessageId, no DB write should happen
        validator.validate(pacs008MissingNbOfTxsXml(), IsoMessageType.PACS_008, null);

        verifyNoInteractions(jdbcTemplate);
    }

    // ── PACS.008 field-level validation ──────────────────────────────────────

    @Test
    void pacs008MissingTxId_failsFieldValidationNotXsd() {
        // TxId is optional in XSD (minOccurs=0) but required by business rules
        IsoXmlValidationResult result = validator.validate(pacs008MissingTxIdXml(), IsoMessageType.PACS_008);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-010", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("TxId"));
    }

    @Test
    void pacs008MissingMsgId_failsFieldValidation() {
        IsoXmlValidationResult result = validator.validate(pacs008MissingMsgIdXml(), IsoMessageType.PACS_008);

        // MsgId is required in XSD too, so this will fail at XSD phase
        assertFalse(result.isValid());
    }

    // ── PACS.002 validation ──────────────────────────────────────────────────

    @Test
    void validPacs002WithAcsc_passesValidation() {
        IsoXmlValidationResult result = validator.validate(validPacs002Xml("ACSC"), IsoMessageType.PACS_002);

        assertTrue(result.isValid());
        assertEquals("PACS_002", result.getDetectedMessageType());
        assertEquals("MSG-PACS002-001", result.getMessageId());
        assertEquals("E2E-001", result.getEndToEndId());
        assertEquals("ACSC", result.getTransactionStatus());
    }

    @Test
    void validPacs002WithRjct_passesValidation() {
        IsoXmlValidationResult result = validator.validate(validPacs002Xml("RJCT"), IsoMessageType.PACS_002);

        assertTrue(result.isValid());
        assertEquals("RJCT", result.getTransactionStatus());
    }

    @Test
    void pacs002UnsupportedTxSts_failsFieldValidation() {
        // "XXXX" is syntactically valid per XSD (1-4 chars) but not a supported status
        IsoXmlValidationResult result = validator.validate(validPacs002Xml("XXXX"), IsoMessageType.PACS_002);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-020", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("XXXX"));
    }

    @Test
    void pacs002MissingGrpHdrMsgId_failsXsdValidation() {
        IsoXmlValidationResult result = validator.validate(
                pacs002MissingGrpHdrMsgIdXml(), IsoMessageType.PACS_002);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-XSD-001", result.getErrorCode());
    }

    // ── Malformed XML ────────────────────────────────────────────────────────

    @Test
    void malformedXml_returnsISO_VAL_999() {
        IsoXmlValidationResult result = validator.validate("<bad xml >>>", null);

        assertFalse(result.isValid());
        assertEquals("ISO-VAL-999", result.getErrorCode());
        assertTrue(result.getErrorMessage().startsWith("Invalid ISO XML:"));
    }

    // ── XML fixtures ─────────────────────────────────────────────────────────

    /** Well-formed PACS.008 that should pass both XSD and field validation. */
    private static String validPacs008Xml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>MSG-PACS008-001</MsgId>
                      <CreDtTm>2026-01-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf>
                        <SttlmMtd>CLRG</SttlmMtd>
                      </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <EndToEndId>E2E-001</EndToEndId>
                        <TxId>TX-001</TxId>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="THB">1000.00</IntrBkSttlmAmt>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
    }

    /** PACS.008 with NbOfTxs omitted — required in GroupHeader93 XSD, so fails at phase 1. */
    private static String pacs008MissingNbOfTxsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>MSG-001</MsgId>
                      <CreDtTm>2026-01-15T10:00:00</CreDtTm>
                      <SttlmInf>
                        <SttlmMtd>CLRG</SttlmMtd>
                      </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <EndToEndId>E2E-001</EndToEndId>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="THB">500.00</IntrBkSttlmAmt>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
    }

    /** PACS.008 where XSD passes (TxId optional in XSD) but field-level check fails. */
    private static String pacs008MissingTxIdXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>MSG-001</MsgId>
                      <CreDtTm>2026-01-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf>
                        <SttlmMtd>CLRG</SttlmMtd>
                      </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <EndToEndId>E2E-001</EndToEndId>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="THB">500.00</IntrBkSttlmAmt>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
    }

    /** PACS.008 with MsgId removed — required in both XSD and field checks. */
    private static String pacs008MissingMsgIdXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <CreDtTm>2026-01-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf>
                        <SttlmMtd>CLRG</SttlmMtd>
                      </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <EndToEndId>E2E-001</EndToEndId>
                        <TxId>TX-001</TxId>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="THB">500.00</IntrBkSttlmAmt>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
    }

    /** Well-formed PACS.002 with the given TxSts code. */
    private static String validPacs002Xml(String txSts) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10">
                  <FIToFIPmtStsRpt>
                    <GrpHdr>
                      <MsgId>MSG-PACS002-001</MsgId>
                      <CreDtTm>2026-01-15T10:05:00</CreDtTm>
                    </GrpHdr>
                    <TxInfAndSts>
                      <OrgnlMsgId>MSG-PACS008-001</OrgnlMsgId>
                      <OrgnlEndToEndId>E2E-001</OrgnlEndToEndId>
                      <OrgnlTxId>TX-001</OrgnlTxId>
                      <TxSts>%s</TxSts>
                    </TxInfAndSts>
                  </FIToFIPmtStsRpt>
                </Document>
                """.formatted(txSts);
    }

    /** PACS.002 with GrpHdr.MsgId removed — required in XSD GroupHeader91. */
    private static String pacs002MissingGrpHdrMsgIdXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10">
                  <FIToFIPmtStsRpt>
                    <GrpHdr>
                      <CreDtTm>2026-01-15T10:05:00</CreDtTm>
                    </GrpHdr>
                    <TxInfAndSts>
                      <OrgnlMsgId>MSG-PACS008-001</OrgnlMsgId>
                      <OrgnlEndToEndId>E2E-001</OrgnlEndToEndId>
                      <OrgnlTxId>TX-001</OrgnlTxId>
                      <TxSts>ACSC</TxSts>
                    </TxInfAndSts>
                  </FIToFIPmtStsRpt>
                </Document>
                """;
    }
}
