package com.example.switching.crossborder.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.switching.aml.dto.ScreeningResult;
import com.example.switching.aml.service.SanctionsScreeningService;
import com.example.switching.common.PhaseIIAuditPublisher;
import com.example.switching.crossborder.adapter.CrossBorderRailAdapter;
import com.example.switching.crossborder.dto.RailInstruction;
import com.example.switching.crossborder.dto.RailTransactionRef;

@Service
public class RailComplianceDispatchService {

    private final List<CrossBorderRailAdapter> adapters;
    private final SanctionsScreeningService sanctions;
    private final PhaseIIAuditPublisher audit;

    public RailComplianceDispatchService(
            List<CrossBorderRailAdapter> adapters,
            SanctionsScreeningService sanctions,
            PhaseIIAuditPublisher audit) {
        this.adapters = adapters;
        this.sanctions = sanctions;
        this.audit = audit;
    }

    public RailTransactionRef submit(String rail, RailInstruction instruction) {
        validate(instruction);
        ScreeningResult screening = sanctions.screen(
                instruction.internalRef(),
                instruction.sourceParticipant(),
                instruction.beneficiaryName());
        if (screening == null || !screening.isClear()) {
            audit.publish(
                    "cross_border.compliance_blocked",
                    "CROSS_BORDER_INSTRUCTION",
                    instruction.internalRef(),
                    "SYSTEM",
                    java.util.Map.of(
                            "rail", normalizeRail(rail),
                            "outcome", screening == null
                                    ? "UNAVAILABLE"
                                    : screening.getOutcome()));
            throw new SecurityException(
                    "Cross-border instruction requires compliance clearance");
        }
        if ("UPI".equalsIgnoreCase(rail)) {
            throw new IllegalStateException(
                    "UPI outward payments remain disabled pending accreditation");
        }
        CrossBorderRailAdapter adapter = adapters.stream()
                .filter(candidate -> candidate.rail().equalsIgnoreCase(rail))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rail is not enabled: " + rail));
        RailTransactionRef result = adapter.submit(instruction);
        audit.publish(
                "cross_border.rail_submitted",
                "CROSS_BORDER_INSTRUCTION",
                instruction.internalRef(),
                "SYSTEM",
                java.util.Map.of(
                        "rail", adapter.rail(),
                        "externalReference", result.externalReference(),
                        "status", result.status()));
        return result;
    }

    private static void validate(RailInstruction instruction) {
        if (instruction == null
                || blank(instruction.internalRef())
                || blank(instruction.sourceParticipant())
                || blank(instruction.destinationParticipant())
                || blank(instruction.beneficiaryName())
                || instruction.sourceAmount() == null
                || instruction.sourceAmount().signum() <= 0
                || blank(instruction.sourceCurrency())
                || blank(instruction.destinationCurrency())) {
            throw new IllegalArgumentException("Invalid cross-border rail instruction");
        }
    }

    private static String normalizeRail(String rail) {
        if (blank(rail)) {
            throw new IllegalArgumentException("Rail is required");
        }
        return rail.toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
