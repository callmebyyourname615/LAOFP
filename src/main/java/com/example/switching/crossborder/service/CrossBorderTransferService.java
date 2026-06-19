package com.example.switching.crossborder.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.aml.service.SanctionsScreeningService;
import com.example.switching.crossborder.adapter.CrossBorderNetworkAdapter;
import com.example.switching.crossborder.config.CrossBorderProperties;
import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.CrossBorderInitiateResponse;
import com.example.switching.crossborder.entity.FxCorridorEntity;
import com.example.switching.crossborder.entity.FxQuoteEntity;
import com.example.switching.crossborder.exception.CorridorNotAvailableException;
import com.example.switching.crossborder.exception.PurposeCodeRequiredException;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.repository.FxQuoteRepository;
import com.example.switching.liquidity.service.PoolService;
import com.example.switching.webhook.service.WebhookEventPublisher;

/**
 * Orchestrates a cross-border payment:
 * FX quote validation → purpose code check → AML screening →
 * pool hold → adapter dispatch → SETTLED transaction → confirm hold → webhook.
 */
@Service
public class CrossBorderTransferService {

    private static final Logger log = LoggerFactory.getLogger(CrossBorderTransferService.class);

    private final FxQuoteService            fxQuoteService;
    private final FxCorridorRepository      corridorRepo;
    private final FxQuoteRepository         quoteRepo;
    private final CrossBorderProperties     props;
    private final List<CrossBorderNetworkAdapter> adapters;
    private final SanctionsScreeningService sanctionsService;
    private final PoolService               poolService;
    private final JdbcTemplate              jdbcTemplate;
    private final WebhookEventPublisher     webhookPublisher;

    public CrossBorderTransferService(
            FxQuoteService fxQuoteService,
            FxCorridorRepository corridorRepo,
            FxQuoteRepository quoteRepo,
            CrossBorderProperties props,
            List<CrossBorderNetworkAdapter> adapters,
            SanctionsScreeningService sanctionsService,
            PoolService poolService,
            JdbcTemplate jdbcTemplate,
            WebhookEventPublisher webhookPublisher) {
        this.fxQuoteService   = fxQuoteService;
        this.corridorRepo     = corridorRepo;
        this.quoteRepo        = quoteRepo;
        this.props            = props;
        this.adapters         = adapters;
        this.sanctionsService = sanctionsService;
        this.poolService      = poolService;
        this.jdbcTemplate     = jdbcTemplate;
        this.webhookPublisher = webhookPublisher;
    }

    /**
     * Initiate a cross-border transfer.
     *
     * @throws com.example.switching.crossborder.exception.FxQuoteExpiredException   if quote expired/used (LFP-CB-001)
     * @throws CorridorNotAvailableException                                          if network not found (LFP-CB-002)
     * @throws PurposeCodeRequiredException                                           if >5M and no purposeCode (LFP-CB-003)
     * @throws com.example.switching.aml.exception.SanctionsBlockException            if beneficiary blocked (LFP-CB-004)
     */
    @Transactional
    public CrossBorderInitiateResponse initiate(CrossBorderInitiateRequest request) {

        // 1. Validate FX quote (throws FxQuoteExpiredException if expired/used)
        FxQuoteEntity quote = fxQuoteService.requireValidQuote(request.quoteId());

        // 2. Load corridor for network info
        FxCorridorEntity corridor = corridorRepo.findById(quote.getCorridorId())
                .orElseThrow(() -> new CorridorNotAvailableException("Corridor not found for quote"));
        String network = corridor.getTargetNetwork();

        // 3. Purpose code enforcement for large amounts
        BigDecimal threshold = BigDecimal.valueOf(props.getPurposeCodeThresholdLak());
        if (quote.getSourceAmount().compareTo(threshold) > 0) {
            boolean missingPurpose = request.purposeCode()   == null || request.purposeCode().isBlank();
            boolean missingSource  = request.sourceOfFunds() == null || request.sourceOfFunds().isBlank();
            if (missingPurpose || missingSource) {
                throw new PurposeCodeRequiredException(quote.getSourceAmount(), props.getPurposeCodeThresholdLak());
            }
        }

        // 4. AML screening for beneficiary name — throws SanctionsBlockException if blocked
        String cbTxnRef = "CB-AML-" + request.quoteId() + "-" + System.nanoTime();
        sanctionsService.screen(cbTxnRef, request.beneficiaryName(), request.beneficiaryName());

        // 5. Get network adapter — resolved at call time (supports mock injection in tests)
        CrossBorderNetworkAdapter adapter = adapters.stream()
                .filter(a -> network.equals(a.targetNetwork()))
                .findFirst()
                .orElseThrow(() -> new CorridorNotAvailableException("No adapter for network: " + network));

        // 6. Hold funds from initiating PSP pool
        String holdRef = "CB-HOLD-" + request.quoteId() + "-" + System.nanoTime();
        poolService.holdFunds(request.initiatingPspId(), holdRef, quote.getSourceAmount());

        // 7. Insert crossborder_transfers INITIATED
        LocalDateTime now = LocalDateTime.now();
        Long cbId = jdbcTemplate.queryForObject(
                """
                INSERT INTO crossborder_transfers
                    (quote_id, initiating_psp_id, purpose_code, source_of_funds,
                     beneficiary_name, beneficiary_bank, beneficiary_account, beneficiary_country,
                     target_network, status, initiated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'INITIATED', ?)
                RETURNING cb_id
                """,
                Long.class,
                quote.getQuoteId(), request.initiatingPspId(),
                request.purposeCode(), request.sourceOfFunds(),
                request.beneficiaryName(), request.beneficiaryBank(),
                request.beneficiaryAccount(), request.beneficiaryCountry(),
                network, now);

        // 8. Dispatch to network adapter (throws CorridorNotAvailableException on failure → full rollback)
        String networkTxnId = adapter.send(request, quote, cbId);

        // 9. Insert SETTLED transaction
        String txnRef = "CB-TXN-" + cbId + "-" + System.nanoTime();
        LocalDate today = LocalDate.now();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    transaction_ref, idempotency_key, flow_ref,
                    source_bank, source_account_no,
                    destination_bank, destination_account_no, destination_account_name,
                    amount, currency, channel_id, route_code, connector_name,
                    status, external_reference, reference,
                    settlement_method, high_value,
                    business_date, accepted_at, settled_at, created_at
                ) VALUES (?, ?, ?, ?, 'CB_SRC', ?, 'CB_DST', ?,
                    ?, ?, 'CROSSBORDER', 'ROUTE_CB', ?,
                    'SETTLED', ?, ?,
                    'DNS', false,
                    ?, ?, ?, ?)
                """,
                txnRef, txnRef, "CB-" + cbId,
                request.initiatingPspId(),
                corridor.getDestCurrency() + "_PARTNER", request.beneficiaryName(),
                quote.getSourceAmount(), quote.getSourceCurrency(),
                network,
                "CB-EXT-" + cbId, txnRef,
                today, now, now, now);

        // 10. Update crossborder_transfers COMPLETED
        jdbcTemplate.update(
                "UPDATE crossborder_transfers SET status='COMPLETED', txn_ref=?, network_txn_id=?, completed_at=? WHERE cb_id=?",
                txnRef, networkTxnId, now, cbId);

        // 11. Mark quote used + confirm hold
        quoteRepo.markUsed(quote.getQuoteId());
        poolService.confirmHold(holdRef);

        log.info("Cross-border transfer completed: cbId={} network={} networkTxnId={}", cbId, network, networkTxnId);

        // 12. Fire webhook
        webhookPublisher.publishQuietly("CROSSBORDER.PAYMENT.COMPLETED",
                request.initiatingPspId(), txnRef,
                Map.of("cbId", cbId, "txnRef", txnRef, "network", network,
                        "networkTxnId", networkTxnId, "amount", quote.getSourceAmount().toPlainString(),
                        "destAmount", quote.getDestAmount().toPlainString()));

        return new CrossBorderInitiateResponse(cbId, txnRef, network, networkTxnId,
                quote.getSourceCurrency(), quote.getSourceAmount(),
                quote.getDestCurrency(), quote.getDestAmount(), quote.getFee(),
                "COMPLETED", now);
    }
}
