package com.example.switching.iso.parser;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.example.switching.iso.dto.IsoXmlValidationResult;
import com.example.switching.iso.enums.IsoMessageType;

import jakarta.annotation.PostConstruct;

@Component
public class IsoXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(IsoXmlValidator.class);

    /** Classpath paths (under resources/) for each message type that has an XSD. */
    private static final Map<IsoMessageType, String> XSD_RESOURCES = Map.of(
            IsoMessageType.PACS_008, "iso-xsd/pacs.008.001.08.xsd",
            IsoMessageType.PACS_002, "iso-xsd/pacs.002.001.10.xsd",
            IsoMessageType.CAMT_056, "iso-xsd/camt.056.001.08.xsd"
    );

    private static final String PERSIST_SQL = """
            INSERT INTO iso_validation_errors
                (iso_message_id, field_path, error_code, error_message, severity, business_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    /** Pre-compiled schemas loaded once at startup. Thread-safe for validation. */
    private final Map<IsoMessageType, Schema> schemaCache = new EnumMap<>(IsoMessageType.class);

    public IsoXmlValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void loadSchemas() {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        for (Map.Entry<IsoMessageType, String> entry : XSD_RESOURCES.entrySet()) {
            IsoMessageType type = entry.getKey();
            String path = entry.getValue();
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    log.warn("XSD not found on classpath '{}' — XSD validation disabled for {}", path, type);
                    continue;
                }
                Schema schema = sf.newSchema(resource.getURL());
                schemaCache.put(type, schema);
                log.info("Loaded ISO 20022 XSD for {}: {}", type, path);
            } catch (Exception e) {
                log.warn("Failed to load XSD for {} ({}): {} — falling back to field-only validation",
                        type, path, e.getMessage());
            }
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Validate XML against detected message type.
     * Errors are NOT persisted to iso_validation_errors (no isoMessageId known).
     */
    public IsoXmlValidationResult validate(String xml, IsoMessageType expectedMessageType) {
        return validate(xml, expectedMessageType, null);
    }

    /**
     * Validate XML with XSD schema + field-level checks.
     * When {@code isoMessageId} is non-null, XSD errors are also persisted
     * to the {@code iso_validation_errors} partitioned table.
     */
    public IsoXmlValidationResult validate(String xml, IsoMessageType expectedMessageType, Long isoMessageId) {

        if (!StringUtils.hasText(xml)) {
            return IsoXmlValidationResult.invalid(
                    "ISO-VAL-001",
                    "ISO XML payload is empty",
                    expectedMessageType == null ? null : expectedMessageType.name()
            );
        }

        try {
            Document document = parseDocument(xml);

            String namespace = document.getDocumentElement() == null
                    ? null
                    : document.getDocumentElement().getNamespaceURI();

            IsoMessageType detectedType = detectMessageType(namespace, document);

            if (detectedType == null) {
                return IsoXmlValidationResult.invalid(
                        "ISO-VAL-002",
                        "Unsupported or unknown ISO 20022 message type",
                        null
                );
            }

            if (expectedMessageType != null && detectedType != expectedMessageType) {
                return IsoXmlValidationResult.invalid(
                        "ISO-VAL-003",
                        "Expected messageType="
                                + expectedMessageType.name()
                                + " but detected="
                                + detectedType.name(),
                        detectedType.name()
                );
            }

            // ── Phase 1: XSD schema validation ───────────────────────────────
            Schema schema = schemaCache.get(detectedType);
            if (schema != null) {
                List<XsdError> xsdErrors = runXsdValidation(schema, xml);
                if (!xsdErrors.isEmpty()) {
                    persistXsdErrors(isoMessageId, detectedType, xsdErrors);
                    String firstMessage = xsdErrors.get(0).message();
                    return IsoXmlValidationResult.invalid(
                            "ISO-VAL-XSD-001",
                            "XSD validation failed (" + xsdErrors.size() + " error(s)): " + firstMessage,
                            detectedType.name()
                    );
                }
            }

            // ── Phase 2: field-level business validation ──────────────────────
            if (detectedType == IsoMessageType.PACS_008) {
                return validatePacs008(document);
            }

            if (detectedType == IsoMessageType.PACS_002) {
                return validatePacs002(document);
            }

            if (detectedType == IsoMessageType.PACS_028) {
                return validatePacs028(document);
            }

            if (detectedType == IsoMessageType.PACS_004) {
                return validatePacs004(document);
            }

            return IsoXmlValidationResult.invalid(
                    "ISO-VAL-004",
                    "Validator not implemented for messageType=" + detectedType.name(),
                    detectedType.name()
            );

        } catch (Exception ex) {
            return IsoXmlValidationResult.invalid(
                    "ISO-VAL-999",
                    "Invalid ISO XML: " + ex.getMessage(),
                    expectedMessageType == null ? null : expectedMessageType.name()
            );
        }
    }

    // ─── XSD validation helpers ───────────────────────────────────────────────

    /** Collected SAX error with severity. */
    private record XsdError(String severity, String message) {}

    private List<XsdError> runXsdValidation(Schema schema, String xml) {
        List<XsdError> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException ex) {
                    errors.add(new XsdError("WARNING", ex.getLocalizedMessage()));
                }

                @Override
                public void error(SAXParseException ex) {
                    errors.add(new XsdError("ERROR", ex.getLocalizedMessage()));
                }

                @Override
                public void fatalError(SAXParseException ex) throws SAXException {
                    errors.add(new XsdError("ERROR", "Fatal: " + ex.getLocalizedMessage()));
                    throw ex; // abort further parsing
                }
            });
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            // fatalError already added, or an unhandled parse failure
            if (errors.isEmpty()) {
                errors.add(new XsdError("ERROR", e.getMessage()));
            }
        } catch (Exception e) {
            errors.add(new XsdError("ERROR", "XSD engine error: " + e.getMessage()));
        }
        return errors;
    }

    private void persistXsdErrors(Long isoMessageId, IsoMessageType messageType, List<XsdError> errors) {
        if (isoMessageId == null || errors.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (XsdError err : errors) {
            String message = err.message();
            if (message != null && message.length() > 2000) {
                message = message.substring(0, 2000);
            }
            try {
                jdbcTemplate.update(PERSIST_SQL,
                        isoMessageId,
                        messageType.name(),  // field_path = message type (structural level)
                        "ISO-XSD-001",
                        message,
                        err.severity(),
                        today
                );
            } catch (Exception e) {
                log.warn("Could not persist XSD error for iso_message_id={}: {}", isoMessageId, e.getMessage());
            }
        }
    }

    // ─── Field-level validators ───────────────────────────────────────────────

    private IsoXmlValidationResult validatePacs008(Document document) {
        String msgId       = text(document, "MsgId");
        String endToEndId  = text(document, "EndToEndId");
        String txId        = text(document, "TxId");
        String amount      = text(document, "IntrBkSttlmAmt");

        if (!StringUtils.hasText(msgId)) {
            return invalidRequired("MsgId", "PACS_008");
        }
        if (!StringUtils.hasText(endToEndId)) {
            return invalidRequired("EndToEndId", "PACS_008");
        }
        if (!StringUtils.hasText(txId)) {
            return invalidRequired("TxId", "PACS_008");
        }
        if (!StringUtils.hasText(amount)) {
            return invalidRequired("IntrBkSttlmAmt", "PACS_008");
        }

        return IsoXmlValidationResult.valid("PACS_008", msgId, endToEndId, null);
    }

    private IsoXmlValidationResult validatePacs002(Document document) {
        String msgId            = text(document, "MsgId");
        String originalMsgId    = text(document, "OrgnlMsgId");
        String originalE2EId    = text(document, "OrgnlEndToEndId");
        String originalTxId     = text(document, "OrgnlTxId");
        String txStatus         = text(document, "TxSts");

        if (!StringUtils.hasText(msgId)) {
            return invalidRequired("MsgId", "PACS_002");
        }
        if (!StringUtils.hasText(originalMsgId)) {
            return invalidRequired("OrgnlMsgId", "PACS_002");
        }
        if (!StringUtils.hasText(originalE2EId)) {
            return invalidRequired("OrgnlEndToEndId", "PACS_002");
        }
        if (!StringUtils.hasText(originalTxId)) {
            return invalidRequired("OrgnlTxId", "PACS_002");
        }
        if (!StringUtils.hasText(txStatus)) {
            return invalidRequired("TxSts", "PACS_002");
        }
        if (!isSupportedPacsStatus(txStatus)) {
            return IsoXmlValidationResult.invalid(
                    "ISO-VAL-020",
                    "Unsupported PACS.002 TxSts=" + txStatus,
                    "PACS_002"
            );
        }

        return IsoXmlValidationResult.valid("PACS_002", msgId, originalE2EId, txStatus);
    }

    private IsoXmlValidationResult validatePacs028(Document document) {
        String msgId         = text(document, "MsgId");
        String originalMsgId = text(document, "OrgnlMsgId");
        String originalE2EId = text(document, "OrgnlEndToEndId");

        if (!StringUtils.hasText(msgId)) {
            return invalidRequired("MsgId", "PACS_028");
        }
        if (!StringUtils.hasText(originalMsgId)) {
            return invalidRequired("OrgnlMsgId", "PACS_028");
        }

        return IsoXmlValidationResult.valid("PACS_028", msgId, originalE2EId, null);
    }

    private IsoXmlValidationResult validatePacs004(Document document) {
        String msgId         = text(document, "MsgId");
        String originalMsgId = text(document, "OrgnlMsgId");
        String originalE2EId = text(document, "OrgnlEndToEndId");
        String originalTxId  = text(document, "OrgnlTxId");

        if (!StringUtils.hasText(msgId)) {
            return invalidRequired("MsgId", "PACS_004");
        }
        if (!StringUtils.hasText(originalMsgId)) {
            return invalidRequired("OrgnlMsgId", "PACS_004");
        }
        if (!StringUtils.hasText(originalE2EId) && !StringUtils.hasText(originalTxId)) {
            return IsoXmlValidationResult.invalid(
                    "ISO-VAL-011",
                    "Either OrgnlEndToEndId or OrgnlTxId is required for PACS.004",
                    "PACS_004"
            );
        }

        return IsoXmlValidationResult.valid("PACS_004", msgId, originalE2EId, null);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private IsoXmlValidationResult invalidRequired(String fieldName, String messageType) {
        return IsoXmlValidationResult.invalid(
                "ISO-VAL-010",
                "Required field is missing: " + fieldName,
                messageType
        );
    }

    private boolean isSupportedPacsStatus(String status) {
        return "ACSC".equalsIgnoreCase(status)
                || "ACCP".equalsIgnoreCase(status)
                || "ACTC".equalsIgnoreCase(status)
                || "RJCT".equalsIgnoreCase(status)
                || "PDNG".equalsIgnoreCase(status);
    }

    private IsoMessageType detectMessageType(String namespace, Document document) {
        if (namespace != null && namespace.contains("pacs.008")) return IsoMessageType.PACS_008;
        if (namespace != null && namespace.contains("pacs.002")) return IsoMessageType.PACS_002;
        if (namespace != null && namespace.contains("pacs.028")) return IsoMessageType.PACS_028;
        if (namespace != null && namespace.contains("pacs.004")) return IsoMessageType.PACS_004;

        if (document.getElementsByTagNameNS("*", "FIToFICstmrCdtTrf").getLength() > 0) return IsoMessageType.PACS_008;
        if (document.getElementsByTagNameNS("*", "FIToFIPmtStsRpt").getLength()  > 0) return IsoMessageType.PACS_002;
        if (document.getElementsByTagNameNS("*", "FIToFIPmtStsReq").getLength()  > 0) return IsoMessageType.PACS_028;
        if (document.getElementsByTagNameNS("*", "PmtRtr").getLength()           > 0) return IsoMessageType.PACS_004;

        return null;
    }

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String text(Document document, String tagName) {
        var nodes = document.getElementsByTagNameNS("*", tagName);
        if (nodes == null || nodes.getLength() == 0) return null;

        var node = nodes.item(0);
        if (node == null || node.getTextContent() == null) return null;

        String value = node.getTextContent().trim();
        return StringUtils.hasText(value) ? value : null;
    }
}
