package com.example.switching.outbox.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * FPRE ambiguous-state credit check.
 *
 * <p>Called by {@link OutboxProcessorService} when {@code failureClass=AMBIGUOUS} and
 * a retry is being scheduled. If the PSP confirms the credit was already applied, the
 * outbox event is settled immediately (avoids duplicate payment on retry).
 *
 * <p>In the mock/dev environment the PSP base URL is not configured, so the check
 * is skipped and the method returns {@link CreditStatusResponse#unknown()} — the
 * processor will schedule a normal retry.
 */
@Service
public class OutboxAmbiguousCheckService {

    private static final Logger log = LoggerFactory.getLogger(OutboxAmbiguousCheckService.class);

    private final RestClient restClient;

    public OutboxAmbiguousCheckService() {
        this.restClient = RestClient.create();
    }

    /**
     * Query the PSP credit-status endpoint.
     *
     * @param pspBaseUrl base URL of the destination PSP (may be null/empty in dev)
     * @param txnId      the transfer reference to check
     * @return credit-status result; never null
     */
    public CreditStatusResponse checkCreditStatus(String pspBaseUrl, String txnId) {
        if (!StringUtils.hasText(pspBaseUrl)) {
            log.debug("Ambiguous credit check skipped — pspBaseUrl not configured for txnId={}", txnId);
            return CreditStatusResponse.unknown();
        }

        String url = pspBaseUrl.stripTrailing() + "/laofp/transactions/" + txnId + "/credit-status";
        try {
            CreditStatusResponse resp = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(CreditStatusResponse.class);
            if (resp == null) {
                return CreditStatusResponse.unknown();
            }
            log.info("Ambiguous credit check: txnId={} creditApplied={}", txnId, resp.creditApplied());
            return resp;
        } catch (RestClientException ex) {
            log.warn("Ambiguous credit check failed for txnId={}: {}", txnId, ex.getMessage());
            return CreditStatusResponse.unknown();
        }
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    public record CreditStatusResponse(boolean creditApplied, boolean known, Instant checkedAt) {

        /** PSP confirmed credit was applied → safe to settle. */
        public static CreditStatusResponse applied() {
            return new CreditStatusResponse(true, true, Instant.now());
        }

        /** PSP confirmed credit was NOT applied → safe to retry. */
        public static CreditStatusResponse notApplied() {
            return new CreditStatusResponse(false, true, Instant.now());
        }

        /** PSP unreachable or gave no definitive answer → schedule retry. */
        public static CreditStatusResponse unknown() {
            return new CreditStatusResponse(false, false, Instant.now());
        }
    }
}
