package com.example.switching.qr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.qr.dto.DecodeQrRequest;
import com.example.switching.qr.dto.GenerateDynamicQrRequest;
import com.example.switching.qr.dto.GenerateStaticQrRequest;
import com.example.switching.qr.dto.PayQrRequest;
import com.example.switching.qr.dto.QrCodeResponse;
import com.example.switching.qr.dto.QrDecodeResponse;
import com.example.switching.qr.dto.QrPayResponse;
import com.example.switching.qr.dto.QrRefundResponse;
import com.example.switching.qr.dto.RefundQrRequest;
import com.example.switching.qr.entity.QrCodeEntity;
import com.example.switching.qr.service.QrDecodeService;
import com.example.switching.qr.service.QrGeneratorService;
import com.example.switching.qr.service.QrPaymentService;
import com.example.switching.qr.service.QrRefundService;

/**
 * REST controller for the QR Code Service (P15).
 *
 * <pre>
 *   POST /v1/qr/generate/static   — generate a reusable STATIC QR (merchant presented)
 *   POST /v1/qr/generate/dynamic  — generate a single-use DYNAMIC QR with fixed amount
 *   POST /v1/qr/decode             — decode + validate a raw EMVCo QR payload
 *   POST /v1/qr/pay                — execute a QR payment (issuing PSP)
 *   POST /v1/qr/refund             — initiate a QR payment refund (within 30 days)
 * </pre>
 */
@RestController
@RequestMapping("/v1/qr")
public class QrController {

    private final QrGeneratorService generatorService;
    private final QrDecodeService    decodeService;
    private final QrPaymentService   paymentService;
    private final QrRefundService    refundService;

    public QrController(QrGeneratorService generatorService,
                         QrDecodeService    decodeService,
                         QrPaymentService   paymentService,
                         QrRefundService    refundService) {
        this.generatorService = generatorService;
        this.decodeService    = decodeService;
        this.paymentService   = paymentService;
        this.refundService    = refundService;
    }

    // ── Generate Static QR ────────────────────────────────────────────────────

    @PostMapping("/generate/static")
    public ResponseEntity<QrCodeResponse> generateStatic(
            @Validated @RequestBody GenerateStaticQrRequest request) {
        QrCodeEntity entity = generatorService.generateStatic(
                request.merchantId(), request.pspId(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    // ── Generate Dynamic QR ───────────────────────────────────────────────────

    @PostMapping("/generate/dynamic")
    public ResponseEntity<QrCodeResponse> generateDynamic(
            @Validated @RequestBody GenerateDynamicQrRequest request) {
        QrCodeEntity entity = generatorService.generateDynamic(
                request.merchantId(),
                request.pspId(),
                request.amount(),
                request.txnRef(),
                request.expiresInSeconds());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    // ── Decode QR ─────────────────────────────────────────────────────────────

    @PostMapping("/decode")
    public ResponseEntity<QrDecodeResponse> decode(
            @Validated @RequestBody DecodeQrRequest request) {
        QrDecodeResponse response = decodeService.decode(request.qrPayload());
        return ResponseEntity.ok(response);
    }

    // ── Pay QR ────────────────────────────────────────────────────────────────

    @PostMapping("/pay")
    public ResponseEntity<QrPayResponse> pay(
            @Validated @RequestBody PayQrRequest request) {
        QrPayResponse response = paymentService.pay(
                request.qrId(),
                request.issuingPspId(),
                request.amount());
        return ResponseEntity.ok(response);
    }

    // ── Refund QR ─────────────────────────────────────────────────────────────

    @PostMapping("/refund")
    public ResponseEntity<QrRefundResponse> refund(
            @Validated @RequestBody RefundQrRequest request) {
        QrRefundResponse response = refundService.refund(
                request.originalTxnId(), request.amount());
        return ResponseEntity.ok(response);
    }

    // ── mapper ────────────────────────────────────────────────────────────────

    private QrCodeResponse toResponse(QrCodeEntity e) {
        return new QrCodeResponse(
                e.getQrId(),
                e.getMerchantId(),
                e.getPspId(),
                e.getQrType(),
                e.getPayloadText(),
                e.getAmount(),
                e.getCurrency(),
                e.getTxnRef(),
                e.getExpiresAt(),
                e.getCreatedAt());
    }
}
