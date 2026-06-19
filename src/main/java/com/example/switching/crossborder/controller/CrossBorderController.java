package com.example.switching.crossborder.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.dto.CrossBorderInitiateResponse;
import com.example.switching.crossborder.dto.FxQuoteRequest;
import com.example.switching.crossborder.dto.FxQuoteResponse;
import com.example.switching.crossborder.dto.FxRateResponse;
import com.example.switching.crossborder.service.CrossBorderTransferService;
import com.example.switching.crossborder.service.FxQuoteService;

/**
 * Cross-border Payment API.
 *
 * <pre>
 * GET  /v1/crossborder/corridors       — list active corridors    (BANK/OPS/ADMIN)
 * GET  /v1/crossborder/fx-rates        — indicative rates         (BANK/OPS/ADMIN)
 * POST /v1/crossborder/quote           — create 30s binding quote (BANK/ADMIN)
 * POST /v1/crossborder/initiate        — execute cross-border pay (BANK/ADMIN)
 * </pre>
 */
@RestController
@RequestMapping("/v1/crossborder")
public class CrossBorderController {

    private final FxQuoteService            fxQuoteService;
    private final CrossBorderTransferService transferService;

    public CrossBorderController(FxQuoteService fxQuoteService,
                                 CrossBorderTransferService transferService) {
        this.fxQuoteService  = fxQuoteService;
        this.transferService = transferService;
    }

    @GetMapping("/corridors")
    public ResponseEntity<List<FxRateResponse>> listCorridors() {
        return ResponseEntity.ok(fxQuoteService.getActiveCorrridors());
    }

    @GetMapping("/fx-rates")
    public ResponseEntity<List<FxRateResponse>> getFxRates(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(fxQuoteService.getIndicativeRates(from, to));
    }

    @PostMapping("/quote")
    public ResponseEntity<FxQuoteResponse> createQuote(
            @Valid @RequestBody FxQuoteRequest request) {
        return ResponseEntity.ok(fxQuoteService.createQuote(request));
    }

    @PostMapping("/initiate")
    public ResponseEntity<CrossBorderInitiateResponse> initiate(
            @Valid @RequestBody CrossBorderInitiateRequest request) {
        return ResponseEntity.ok(transferService.initiate(request));
    }
}
