package com.example.switching.iso.inbound;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.exception.ScreeningTimeoutException;
import com.example.switching.aml.service.SanctionsScreeningService;
import com.example.switching.common.util.MaskingUtil;
import com.example.switching.risk.dto.FraudScore;
import com.example.switching.risk.service.FraudScoringService;
import com.example.switching.risk.service.VelocityCheckService;

@Service
public class IsoPacs008InboundService {

    private static final Logger log = LoggerFactory.getLogger(IsoPacs008InboundService.class);

    private final Pacs008InboundParser parser;
    private final Pacs002XmlResponseBuilder responseBuilder;
    private final InboundPacs008PersistenceService persistenceService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final FraudScoringService fraudScoringService;
    private final VelocityCheckService velocityCheckService;

    public IsoPacs008InboundService(
            Pacs008InboundParser parser,
            Pacs002XmlResponseBuilder responseBuilder,
            InboundPacs008PersistenceService persistenceService,
            SanctionsScreeningService sanctionsScreeningService,
            FraudScoringService fraudScoringService,
            VelocityCheckService velocityCheckService
    ) {
        this.parser = parser;
        this.responseBuilder = responseBuilder;
        this.persistenceService = persistenceService;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.fraudScoringService = fraudScoringService;
        this.velocityCheckService = velocityCheckService;
    }

    public String handleInboundPacs008(String xmlBody, String bankCodeHeader) {
        log.debug("PACS.008 received from bankCode={}", bankCodeHeader);
        if (log.isDebugEnabled()) {
            log.debug("PACS.008 payload (accounts masked): {}", MaskingUtil.maskXmlAccounts(xmlBody));
        }

        if (isBlank(xmlBody)) {
            return responseBuilder.rejectedWithoutOriginalMessage(
                    "FF01",
                    "PACS.008 XML body is required"
            );
        }

        Pacs008InboundRequest request;

        try {
            request = parser.parse(xmlBody);
        } catch (Exception e) {
            return responseBuilder.rejectedWithoutOriginalMessage(
                    "FF01",
                    "Invalid PACS.008 XML"
            );
        }

        List<String> errors = validate(request, bankCodeHeader);

        if (!errors.isEmpty()) {
            return responseBuilder.rejected(
                    request,
                    "FF01",
                    String.join("; ", errors)
            );
        }

        // ── AML sanctions screening ───────────────────────────────────────────
        // Screen debtor and creditor agent BICs as party identifiers.
        // In production, party names from DbtrNm/CdtrNm elements would be used.
        try {
            sanctionsScreeningService.screen(
                    request.getMessageId(),
                    request.getDebtorAgentBic(),
                    request.getCreditorAgentBic());
        } catch (SanctionsBlockException e) {
            log.warn("PACS.008 rejected — sanctions block: msgId={} entity={}",
                    request.getMessageId(), e.getMatchedEntity());
            return responseBuilder.rejected(request, "AM05",
                    "Transaction blocked: sanctions match on " + e.getMatchedEntity());
        } catch (ScreeningTimeoutException e) {
            log.warn("PACS.008 rejected — screening timeout: msgId={}", request.getMessageId());
            return responseBuilder.rejected(request, "MS03", "Sanctions screening unavailable");
        }

        // ── Velocity + fraud scoring ─────────────────────────────────────────
        try {
            var velocity = velocityCheckService.checkVelocity(
                    request.getDebtorAgentBic(), request.getAmount());
            if (!velocity.isWithinLimits()) {
                log.warn("PACS.008 rejected — velocity limit: msgId={} rule={}",
                        request.getMessageId(), velocity.getBreachedRule());
                return responseBuilder.rejected(request, "AM04",
                        "Velocity limit exceeded: " + velocity.getBreachedRule());
            }

            FraudScore fraudScore = fraudScoringService.score(
                    request.getMessageId(),
                    request.getAmount(),
                    request.getDebtorAgentBic(),
                    request.getCreditorAgentBic());
            if (fraudScore.isBlocked()) {
                log.warn("PACS.008 rejected — high risk: msgId={} tier={}",
                        request.getMessageId(), fraudScore.getRiskTier());
                return responseBuilder.rejected(request, "AM05",
                        "Transaction blocked: high fraud risk (" + fraudScore.getRiskTier() + ")");
            }
        } catch (SanctionsBlockException | ScreeningTimeoutException e) {
            throw e; // re-throw AML exceptions
        } catch (Exception e) {
            // Fail-open for risk scoring
            log.warn("Risk scoring failed (fail-open): msgId={} error={}",
                    request.getMessageId(), e.getMessage());
        }

        try {
            InboundPacs008PersistResult result =
                    persistenceService.persistAcceptedInboundPacs008(request, xmlBody);

            return responseBuilder.accepted(request, result.getTransferRef());
        } catch (Exception e) {
            return responseBuilder.rejected(
                    request,
                    "MS03",
                    "Unable to persist inbound PACS.008: " + safeErrorMessage(e)
            );
        }
    }

    private List<String> validate(Pacs008InboundRequest request, String bankCodeHeader) {
        List<String> errors = new ArrayList<>();

        require(errors, request.getMessageId(), "GrpHdr/MsgId is required");
        require(errors, request.getCreationDateTime(), "GrpHdr/CreDtTm is required");
        require(errors, request.getNumberOfTransactions(), "GrpHdr/NbOfTxs is required");

        if (!"1".equals(request.getNumberOfTransactions())) {
            errors.add("Only single-transaction PACS.008 is supported in ISO-IN-1B.1");
        }

        require(errors, request.getInstructionId(), "PmtId/InstrId is required");
        require(errors, request.getEndToEndId(), "PmtId/EndToEndId is required");

        if (request.getAmount() == null) {
            errors.add("IntrBkSttlmAmt is required and must be numeric");
        } else if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("IntrBkSttlmAmt must be greater than zero");
        }

        require(errors, request.getCurrency(), "IntrBkSttlmAmt/@Ccy is required");
        require(errors, request.getDebtorAgentBic(), "DbtrAgt/FinInstnId/BICFI is required");
        require(errors, request.getCreditorAgentBic(), "CdtrAgt/FinInstnId/BICFI is required");
        require(errors, request.getDebtorAccount(), "DbtrAcct/Id/Othr/Id is required");
        require(errors, request.getCreditorAccount(), "CdtrAcct/Id/Othr/Id is required");

        if (isBlank(bankCodeHeader)) {
            errors.add("X-Bank-Code header is required");
        } else if (!isBlank(request.getDebtorAgentBic())
                && !bankCodeHeader.trim().equalsIgnoreCase(request.getDebtorAgentBic().trim())) {
            errors.add("X-Bank-Code must match DbtrAgt/FinInstnId/BICFI");
        }

        return errors;
    }

    private void require(List<String> errors, String value, String message) {
        if (isBlank(value)) {
            errors.add(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeErrorMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}