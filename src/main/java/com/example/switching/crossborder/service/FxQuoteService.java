package com.example.switching.crossborder.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.crossborder.config.CrossBorderProperties;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.dto.FxRateResponse;
import com.example.switching.crossborder.entity.FxCorridorEntity;
import com.example.switching.crossborder.entity.FxQuoteEntity;
import com.example.switching.crossborder.exception.CorridorNotAvailableException;
import com.example.switching.crossborder.repository.FxCorridorRepository;
import com.example.switching.crossborder.repository.FxQuoteRepository;

/**
 * Manages FX rate enquiry and binding 30-second quotes.
 */
@Service
public class FxQuoteService {

    private final FxCorridorRepository corridorRepo;
    private final FxQuoteRepository    quoteRepo;
    private final CrossBorderProperties props;

    public FxQuoteService(FxCorridorRepository corridorRepo,
                          FxQuoteRepository quoteRepo,
                          CrossBorderProperties props) {
        this.corridorRepo = corridorRepo;
        this.quoteRepo    = quoteRepo;
        this.props        = props;
    }

    // ── List corridors / indicative rates ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FxRateResponse> getActiveCorrridors() {
        return corridorRepo.findByStatus("ACTIVE").stream()
                .map(this::toRateResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getIndicativeRates(String from, String to) {
        List<FxCorridorEntity> corridors = (from != null && to != null)
                ? corridorRepo.findBySourceCurrencyAndDestCurrencyAndStatus(from, to, "ACTIVE")
                : corridorRepo.findByStatus("ACTIVE");
        return corridors.stream().map(this::toRateResponse).toList();
    }

    // ── Create binding quote ──────────────────────────────────────────────────

    /**
     * Create a 30-second binding FX quote.
     *
     * @throws CorridorNotAvailableException if corridor not found, not ACTIVE,
     *                                        or amount is outside limits
     */
    @Transactional
    public FxQuoteResponse createQuote(FxQuoteRequest request) {
        FxCorridorEntity corridor = corridorRepo.findById(request.corridorId())
                .orElseThrow(() -> new CorridorNotAvailableException("corridor:" + request.corridorId()));

        if (!"ACTIVE".equals(corridor.getStatus())) {
            throw new CorridorNotAvailableException("Corridor " + request.corridorId() + " is SUSPENDED");
        }
        if (request.amount().compareTo(corridor.getMinAmount()) < 0) {
            throw new CorridorNotAvailableException("Amount below minimum " + corridor.getMinAmount());
        }
        if (request.amount().compareTo(corridor.getMaxAmount()) > 0) {
            throw new CorridorNotAvailableException("Amount above maximum " + corridor.getMaxAmount());
        }

        // fee = feeFixed + amount * feePercent
        BigDecimal fee = corridor.getFeeFixed()
                .add(request.amount().multiply(corridor.getFeePercent()))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal transferable = request.amount().subtract(fee);
        BigDecimal destAmount   = transferable.multiply(corridor.getIndicativeRate())
                .setScale(4, RoundingMode.HALF_UP);

        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(props.getQuoteTtlSeconds());

        FxQuoteEntity quote = new FxQuoteEntity();
        quote.setCorridorId(corridor.getCorridorId());
        quote.setSourceCurrency(corridor.getSourceCurrency());
        quote.setDestCurrency(corridor.getDestCurrency());
        quote.setSourceAmount(request.amount());
        quote.setDestAmount(destAmount);
        quote.setRate(corridor.getIndicativeRate());
        quote.setFee(fee);
        quote.setIssuedAt(now);
        quote.setExpiresAt(expiresAt);
        quote.setUsed(false);
        quote = quoteRepo.save(quote);

        return new FxQuoteResponse(quote.getQuoteId(),
                corridor.getSourceCurrency(), corridor.getDestCurrency(),
                corridor.getTargetNetwork(),
                request.amount(), destAmount, corridor.getIndicativeRate(), fee,
                now, expiresAt);
    }

    // ── Load quote (used by CrossBorderTransferService) ───────────────────────

    @Transactional(readOnly = true)
    public FxQuoteEntity requireValidQuote(Long quoteId) {
        FxQuoteEntity q = quoteRepo.findById(quoteId)
                .orElseThrow(() -> new CorridorNotAvailableException("Quote not found: " + quoteId));
        if (q.isUsed() || LocalDateTime.now().isAfter(q.getExpiresAt())) {
            throw new com.example.switching.crossborder.exception.FxQuoteExpiredException(quoteId);
        }
        return q;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FxRateResponse toRateResponse(FxCorridorEntity c) {
        return new FxRateResponse(c.getCorridorId(), c.getSourceCurrency(), c.getDestCurrency(),
                c.getTargetNetwork(), c.getIndicativeRate(), c.getFeePercent(), c.getFeeFixed(),
                c.getMinAmount(), c.getMaxAmount(), props.getQuoteTtlSeconds());
    }
}
