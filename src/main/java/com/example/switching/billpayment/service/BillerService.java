package com.example.switching.billpayment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.billpayment.client.BillerApiClient;
import com.example.switching.billpayment.client.BillerApiClient.BillerBillDto;
import com.example.switching.billpayment.config.BillPaymentProperties;
import com.example.switching.billpayment.dto.BillFetchResponse;
import com.example.switching.billpayment.dto.BillerResponse;
import com.example.switching.billpayment.entity.BillerEntity;
import com.example.switching.billpayment.entity.BillTokenEntity;
import com.example.switching.billpayment.exception.BillNotFoundException;
import com.example.switching.billpayment.repository.BillerRepository;
import com.example.switching.billpayment.repository.BillTokenRepository;

/**
 * Manages biller catalogue and bill-fetch token lifecycle.
 */
@Service
public class BillerService {

    private static final Logger log = LoggerFactory.getLogger(BillerService.class);

    private final BillerRepository       billerRepo;
    private final BillTokenRepository    tokenRepo;
    private final BillerApiClient        billerApiClient;
    private final BillPaymentProperties  props;

    public BillerService(BillerRepository billerRepo,
                         BillTokenRepository tokenRepo,
                         BillerApiClient billerApiClient,
                         BillPaymentProperties props) {
        this.billerRepo      = billerRepo;
        this.tokenRepo       = tokenRepo;
        this.billerApiClient = billerApiClient;
        this.props           = props;
    }

    // ── List billers ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillerResponse> findActiveBillers() {
        return billerRepo.findByStatus("ACTIVE").stream()
                .map(b -> new BillerResponse(
                        b.getBillerId(), b.getBillerCode(), b.getBillerName(), b.getCategory(), b.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BillerResponse getBiller(Long billerId) {
        BillerEntity b = billerRepo.findById(billerId)
                .orElseThrow(() -> new BillNotFoundException("Biller " + billerId));
        return new BillerResponse(b.getBillerId(), b.getBillerCode(), b.getBillerName(), b.getCategory(), b.getStatus());
    }

    // ── Fetch bill ────────────────────────────────────────────────────────────

    /**
     * Call the external biller API to fetch bill detail, persist a 10-min token,
     * and return the token info to the PSP.
     *
     * @param billerId  ID of the biller
     * @param billRef   biller's bill reference number
     * @throws BillNotFoundException  if biller ID not found or biller returns 404
     * @throws com.example.switching.billpayment.exception.BillerTimeoutException if biller is unreachable
     */
    @Transactional
    public BillFetchResponse fetchBill(Long billerId, String billRef) {
        BillerEntity biller = billerRepo.findById(billerId)
                .orElseThrow(() -> new BillNotFoundException("Biller " + billerId));

        if (!"ACTIVE".equals(biller.getStatus())) {
            throw new BillNotFoundException("Biller is not active: " + biller.getBillerCode());
        }

        // Call external biller
        BillerBillDto billDetail = billerApiClient.fetchBill(
                biller.getApiUrl(), biller.getApiKeyHash(),
                billRef, biller.getTimeoutSeconds());

        // Persist token
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(props.getTokenTtlMinutes());

        BillTokenEntity token = new BillTokenEntity();
        token.setBillerId(biller.getBillerId());
        token.setBillRef(billDetail.billRef());
        token.setBillAmount(billDetail.amount());
        token.setDueDate(billDetail.dueDate());
        token.setCustomerName(billDetail.customerName());
        token.setFetchedAt(now);
        token.setExpiresAt(expiresAt);
        token.setUsed(false);
        token = tokenRepo.save(token);

        log.info("Bill token issued: billerId={} billRef={} tokenId={} expiresAt={}",
                billerId, billRef, token.getTokenId(), expiresAt);

        return new BillFetchResponse(
                token.getTokenId(),
                biller.getBillerId(),
                biller.getBillerCode(),
                billDetail.billRef(),
                billDetail.amount(),
                billDetail.dueDate(),
                billDetail.customerName(),
                expiresAt);
    }
}
