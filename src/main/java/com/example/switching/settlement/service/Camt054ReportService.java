package com.example.switching.settlement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.iso.mapper.Camt054XmlBuilder;
import com.example.switching.settlement.entity.SettlementCycleEntity;
import com.example.switching.settlement.entity.SettlementPositionEntity;
import com.example.switching.settlement.entity.SettlementReportEntity;
import com.example.switching.settlement.repository.SettlementPositionRepository;
import com.example.switching.settlement.repository.SettlementReportRepository;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Generates ISO 20022 camt.054 BankToCustomerDebitCreditNotification reports
 * for every PSP that has a net position in a settled DNS cycle.
 *
 * <p>Called by {@link SettlementNetPositionService#settle(String)} immediately
 * after the cycle transitions to SETTLED. Any exception is caught and logged so
 * that a report-generation failure never rolls back the settle transaction.
 *
 * <h3>Per-PSP lifecycle</h3>
 * <ol>
 *   <li>Build camt.054 XML with gross debit, gross credit, and net position.</li>
 *   <li>Persist to {@code settlement_reports} — idempotent (UNIQUE constraint on
 *       {@code cycle_id, psp_id, report_type}).</li>
 *   <li>Fire {@code SETTLEMENT.CYCLE.COMPLETED} webhook to that PSP via
 *       {@link WebhookEventPublisher} (fire-and-quiet).</li>
 * </ol>
 *
 * <p>Injects {@link SettlementPositionRepository} directly (not
 * {@link SettlementNetPositionService}) to avoid a circular Spring bean dependency.
 */
@Service
public class Camt054ReportService {

    private static final Logger log = LoggerFactory.getLogger(Camt054ReportService.class);

    private final SettlementCycleService       cycleService;
    private final SettlementPositionRepository positionRepository;
    private final SettlementReportRepository   reportRepository;
    private final Camt054XmlBuilder            xmlBuilder;
    private final WebhookEventPublisher        webhookPublisher;

    public Camt054ReportService(
            SettlementCycleService cycleService,
            SettlementPositionRepository positionRepository,
            SettlementReportRepository reportRepository,
            Camt054XmlBuilder xmlBuilder,
            WebhookEventPublisher webhookPublisher) {
        this.cycleService       = cycleService;
        this.positionRepository = positionRepository;
        this.reportRepository   = reportRepository;
        this.xmlBuilder         = xmlBuilder;
        this.webhookPublisher   = webhookPublisher;
    }

    /**
     * Generate camt.054 settlement reports for all PSPs that participated
     * in the given (SETTLED) cycle.
     *
     * @param cycleRef identifies the cycle
     * @return the list of report entities created (or already existing)
     */
    @Transactional
    public List<SettlementReportEntity> generateReportsForCycle(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);

        if (!"SETTLED".equals(cycle.getStatus())) {
            throw new IllegalStateException(
                    "Cannot generate camt.054 reports — cycle " + cycleRef
                    + " is not SETTLED (current status: " + cycle.getStatus() + ")");
        }

        List<SettlementPositionEntity> positions =
                positionRepository.findByCycleIdOrderByBankCodeAsc(cycle.getId());
        List<SettlementReportEntity> generated   = new ArrayList<>();

        for (SettlementPositionEntity pos : positions) {
            String pspId = pos.getBankCode();

            // Idempotency: skip if report already exists for this PSP+cycle
            Optional<SettlementReportEntity> existing =
                    reportRepository.findByCycleIdAndPspIdAndReportType(
                            cycle.getId(), pspId, "CAMT054");
            if (existing.isPresent()) {
                log.debug("camt.054 report already exists for cycleRef={} pspId={}", cycleRef, pspId);
                generated.add(existing.get());
                continue;
            }

            // Build XML
            String reportRef = buildReportRef(cycleRef, pspId);
            String msgId     = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

            BigDecimal grossDebit  = pos.getDebitAmount()  != null ? pos.getDebitAmount()  : BigDecimal.ZERO;
            BigDecimal grossCredit = pos.getCreditAmount() != null ? pos.getCreditAmount() : BigDecimal.ZERO;
            String currency        = pos.getCurrency()     != null ? pos.getCurrency()     : "LAK";

            String xml = xmlBuilder.build(
                    msgId,
                    cycleRef,
                    pspId,
                    grossDebit,
                    grossCredit,
                    currency,
                    cycle.getSettledAt());

            // Persist
            SettlementReportEntity report = new SettlementReportEntity();
            report.setCycleId(cycle.getId());
            report.setPspId(pspId);
            report.setReportType("CAMT054");
            report.setReportRef(reportRef);
            report.setContent(xml);
            report = reportRepository.save(report);
            generated.add(report);

            log.info("camt.054 report generated: cycleRef={} pspId={} reportRef={}",
                    cycleRef, pspId, reportRef);

            // Fire webhook — fire-and-quiet; never block settle
            BigDecimal netPosition = pos.getNetPosition();
            boolean    netCredit   = netPosition != null && netPosition.compareTo(BigDecimal.ZERO) >= 0;
            webhookPublisher.settlementCycleCompleted(
                    cycleRef,
                    pspId,
                    Map.of(
                            "cycleRef",      cycleRef,
                            "settlementDate", cycle.getSettlementDate().toString(),
                            "reportRef",     reportRef,
                            "pspId",         pspId,
                            "grossDebit",    grossDebit.toPlainString(),
                            "grossCredit",   grossCredit.toPlainString(),
                            "netPosition",   netPosition != null ? netPosition.toPlainString() : "0",
                            "netDirection",  netCredit ? "CRDT" : "DBIT",
                            "currency",      currency));
        }

        log.info("camt.054 reports generated for cycleRef={}: {} PSPs", cycleRef, generated.size());
        return generated;
    }

    /**
     * Retrieve an existing camt.054 report for a specific PSP and cycle.
     *
     * @param cycleRef identifies the cycle
     * @param pspId    the PSP's bank code
     * @return the report entity, or empty if none exists
     */
    @Transactional(readOnly = true)
    public Optional<SettlementReportEntity> getReport(String cycleRef, String pspId) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        return reportRepository.findByCycleIdAndPspIdAndReportType(
                cycle.getId(), pspId, "CAMT054");
    }

    /**
     * List all camt.054 reports generated for a cycle (all PSPs).
     *
     * @param cycleRef identifies the cycle
     * @return all report entities ordered by psp_id ascending
     */
    @Transactional(readOnly = true)
    public List<SettlementReportEntity> listForCycle(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        return reportRepository.findByCycleIdOrderByPspIdAsc(cycle.getId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildReportRef(String cycleRef, String pspId) {
        return "RPT-" + cycleRef + "-" + pspId;
    }
}
