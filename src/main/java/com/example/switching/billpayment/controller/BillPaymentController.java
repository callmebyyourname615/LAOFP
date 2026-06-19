package com.example.switching.billpayment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.billpayment.dto.BillFetchResponse;
import com.example.switching.billpayment.dto.BillPayRequest;
import com.example.switching.billpayment.dto.BillPayResponse;
import com.example.switching.billpayment.dto.BillerResponse;
import com.example.switching.billpayment.service.BillPaymentService;
import com.example.switching.billpayment.service.BillerService;

/**
 * Bill Payment API.
 *
 * <pre>
 * GET  /v1/billers                          — list ACTIVE billers (BANK/OPS/ADMIN)
 * GET  /v1/billers/{billerId}               — biller detail   (BANK/OPS/ADMIN)
 * GET  /v1/bills/fetch?billerId=&ref=       — fetch bill + issue 10-min token (BANK/ADMIN)
 * POST /v1/bills/pay                        — pay bill with token (BANK/ADMIN)
 * </pre>
 */
@RestController
@RequestMapping
public class BillPaymentController {

    private final BillerService       billerService;
    private final BillPaymentService  paymentService;

    public BillPaymentController(BillerService billerService,
                                 BillPaymentService paymentService) {
        this.billerService  = billerService;
        this.paymentService = paymentService;
    }

    // ── GET /v1/billers ───────────────────────────────────────────────────────

    @GetMapping("/v1/billers")
    public ResponseEntity<List<BillerResponse>> listBillers() {
        return ResponseEntity.ok(billerService.findActiveBillers());
    }

    // ── GET /v1/billers/{billerId} ────────────────────────────────────────────

    @GetMapping("/v1/billers/{billerId}")
    public ResponseEntity<BillerResponse> getBiller(@PathVariable Long billerId) {
        return ResponseEntity.ok(billerService.getBiller(billerId));
    }

    // ── GET /v1/bills/fetch ───────────────────────────────────────────────────

    @GetMapping("/v1/bills/fetch")
    public ResponseEntity<BillFetchResponse> fetchBill(
            @RequestParam Long   billerId,
            @RequestParam String ref) {
        return ResponseEntity.ok(billerService.fetchBill(billerId, ref));
    }

    // ── POST /v1/bills/pay ────────────────────────────────────────────────────

    @PostMapping("/v1/bills/pay")
    public ResponseEntity<BillPayResponse> pay(@Valid @RequestBody BillPayRequest request) {
        return ResponseEntity.ok(paymentService.pay(request.tokenId(), request.payingPspId()));
    }
}
