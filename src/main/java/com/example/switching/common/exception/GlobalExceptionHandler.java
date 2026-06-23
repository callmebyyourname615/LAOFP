package com.example.switching.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.switching.common.dto.ApiErrorResponse;
import com.example.switching.common.error.ErrorCatalog;
import com.example.switching.common.filter.RequestIdFilter;
import com.example.switching.observability.tracing.TraceContextSupport;
import com.example.switching.connector.exception.ConnectorConfigAlreadyExistsException;
import com.example.switching.connector.exception.ConnectorConfigNotFoundException;
import com.example.switching.fpre.exception.AmbiguousStateException;
import com.example.switching.fpre.exception.AutoReversalException;
import com.example.switching.fpre.exception.MaxRetriesExceededException;
import com.example.switching.idempotency.exception.IdempotencyConflictException;
import com.example.switching.outbox.exception.OutboxEventNotFoundException;
import com.example.switching.outbox.exception.OutboxManualRetryNotAllowedException;
import com.example.switching.inquiry.exception.InquiryNotFoundException;
import com.example.switching.iso.exception.IsoMessageCryptoException;
import com.example.switching.iso.exception.IsoMessageInvalidStateException;
import com.example.switching.iso.exception.IsoMessageNotFoundException;
import com.example.switching.participant.exception.ParticipantAlreadyExistsException;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.exception.ParticipantSuspendedException;
import com.example.switching.participant.exception.ParticipantUnavailableException;
import com.example.switching.routing.exception.RoutingRuleAlreadyExistsException;
import com.example.switching.routing.exception.RoutingRuleNotFoundException;
import com.example.switching.security.mtls.MtlsCertInvalidException;
import com.example.switching.security.oauth.OAuthTokenInvalidException;
import com.example.switching.security.signing.SignatureVerificationException;
import com.example.switching.aml.exception.SanctionsBlockException;
import com.example.switching.aml.exception.ScreeningTimeoutException;
import com.example.switching.liquidity.exception.InsufficientPoolBalanceException;
import com.example.switching.liquidity.exception.PoolHoldNotFoundException;
import com.example.switching.risk.exception.HighRiskBlockException;
import com.example.switching.risk.exception.VelocityLimitException;
import com.example.switching.transfer.exception.InquiryAlreadyUsedException;
import com.example.switching.transfer.exception.InquiryValidationException;
import com.example.switching.transfer.exception.TransferNotFoundException;
import com.example.switching.vpa.exception.BeneficiaryTokenExpiredException;
import com.example.switching.vpa.exception.BeneficiaryTokenUsedException;
import com.example.switching.vpa.exception.VpaDuplicateException;
import com.example.switching.vpa.exception.VpaNotFoundException;
import com.example.switching.qr.exception.DuplicateTxnRefException;
import com.example.switching.qr.exception.MerchantNotActiveException;
import com.example.switching.qr.exception.QrAlreadyUsedException;
import com.example.switching.qr.exception.QrChecksumException;
import com.example.switching.qr.exception.QrExpiredException;
import com.example.switching.qr.exception.QrNotFoundException;
import com.example.switching.qr.exception.QrRefundWindowExpiredException;
import com.example.switching.billpayment.exception.BillNotFoundException;
import com.example.switching.billpayment.exception.BillTokenExpiredException;
import com.example.switching.billpayment.exception.BillerTimeoutException;
import com.example.switching.billpayment.exception.DuplicateBillPaymentException;
import com.example.switching.dispute.exception.DisputeAlreadyExistsException;
import com.example.switching.dispute.exception.DisputeNotAuthorizedException;
import com.example.switching.dispute.exception.DisputeNotFoundException;
import com.example.switching.dispute.exception.DisputeTypeInvalidException;
import com.example.switching.dispute.exception.DisputeWindowExpiredException;
import com.example.switching.crossborder.exception.CorridorNotAvailableException;
import com.example.switching.crossborder.exception.FxQuoteExpiredException;
import com.example.switching.crossborder.exception.PurposeCodeRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
        private final TraceContextSupport traceContext;

        public GlobalExceptionHandler(TraceContextSupport traceContext) {
                this.traceContext = traceContext;
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                Map<String, Object> details = new LinkedHashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
                ex.getBindingResult().getGlobalErrors()
                                .forEach(error -> details.put(error.getObjectName(), error.getDefaultMessage()));

                return buildResponse(
                                ErrorCatalog.REQ_001,
                                "Request validation failed",
                                request,
                                details);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
                        ConstraintViolationException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.REQ_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.REQ_002,
                                "Malformed JSON request",
                                request,
                                null);
        }

        @ExceptionHandler(InquiryValidationException.class)
        public ResponseEntity<ApiErrorResponse> handleInquiryValidation(
                        InquiryValidationException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.INQ_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(InquiryNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleInquiryNotFound(
                        InquiryNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.INQ_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(InquiryAlreadyUsedException.class)
        public ResponseEntity<ApiErrorResponse> handleInquiryAlreadyUsed(
                        InquiryAlreadyUsedException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.INQ_003,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(TransferNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleTransferNotFound(
                        TransferNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.TRF_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(IdempotencyConflictException.class)
        public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
                        IdempotencyConflictException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.TRF_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.INF_DB_002,
                                "Database constraint violation",
                                request,
                                null);
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
                        HttpRequestMethodNotSupportedException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.REQ_003,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(AuthorizationDeniedException.class)
        public ResponseEntity<ApiErrorResponse> handleAuthorizationDenied(
                        AuthorizationDeniedException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_2004,
                                "Access denied",
                                request,
                                null);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleGenericException(
                        Exception ex,
                        HttpServletRequest request) {

                log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
                return buildResponse(
                                ErrorCatalog.SYS_001,
                                "Internal server error",
                                request,
                                null);
        }

 

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
                        IllegalArgumentException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.REQ_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ParticipantAlreadyExistsException.class)
        public ResponseEntity<ApiErrorResponse> handleParticipantAlreadyExists(
                        ParticipantAlreadyExistsException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.PRT_003,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(RoutingRuleAlreadyExistsException.class)
        public ResponseEntity<ApiErrorResponse> handleRoutingRuleAlreadyExists(
                        RoutingRuleAlreadyExistsException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.RTE_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ConnectorConfigAlreadyExistsException.class)
        public ResponseEntity<ApiErrorResponse> handleConnectorConfigAlreadyExists(
                        ConnectorConfigAlreadyExistsException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.CON_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ParticipantNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleParticipantNotFound(
                        ParticipantNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.PRT_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ParticipantUnavailableException.class)
        public ResponseEntity<ApiErrorResponse> handleParticipantUnavailable(
                        ParticipantUnavailableException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.PRT_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(RoutingRuleNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleRoutingRuleNotFound(
                        RoutingRuleNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.RTE_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ConnectorConfigNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleConnectorConfigNotFound(
                        ConnectorConfigNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.CON_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(IsoMessageNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleIsoMessageNotFound(
                        IsoMessageNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.ISO_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(IsoMessageInvalidStateException.class)
        public ResponseEntity<ApiErrorResponse> handleIsoMessageInvalidState(
                        IsoMessageInvalidStateException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.ISO_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(IsoMessageCryptoException.class)
        public ResponseEntity<ApiErrorResponse> handleIsoMessageCrypto(
                        IsoMessageCryptoException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.ISO_003,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(OutboxEventNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleOutboxEventNotFound(
                        OutboxEventNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.OUT_005,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(OutboxManualRetryNotAllowedException.class)
        public ResponseEntity<ApiErrorResponse> handleOutboxManualRetryNotAllowed(
                        OutboxManualRetryNotAllowedException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.OUT_004,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(SignatureVerificationException.class)
        public ResponseEntity<ApiErrorResponse> handleSignatureVerification(
                        SignatureVerificationException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_2003,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(OAuthTokenInvalidException.class)
        public ResponseEntity<ApiErrorResponse> handleOAuthTokenInvalid(
                        OAuthTokenInvalidException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_2001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(MtlsCertInvalidException.class)
        public ResponseEntity<ApiErrorResponse> handleMtlsCertInvalid(
                        MtlsCertInvalidException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_2002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(ParticipantSuspendedException.class)
        public ResponseEntity<ApiErrorResponse> handleParticipantSuspended(
                        ParticipantSuspendedException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_2004,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(MaxRetriesExceededException.class)
        public ResponseEntity<ApiErrorResponse> handleMaxRetriesExceeded(
                        MaxRetriesExceededException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_FPRE_001,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(AutoReversalException.class)
        public ResponseEntity<ApiErrorResponse> handleAutoReversal(
                        AutoReversalException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_FPRE_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        @ExceptionHandler(AmbiguousStateException.class)
        public ResponseEntity<ApiErrorResponse> handleAmbiguousState(
                        AmbiguousStateException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_FPRE_003,
                                ex.getMessage(),
                                request,
                                null);
        }

        // ── AML / CFT exception handlers ─────────────────────────────────────────

        @ExceptionHandler(SanctionsBlockException.class)
        public ResponseEntity<ApiErrorResponse> handleSanctionsBlock(
                        SanctionsBlockException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_SANCTIONS_001,
                                ex.getMessage(),
                                request,
                                Map.of("matchedEntity", ex.getMatchedEntity(),
                                       "listType",      ex.getListType()));
        }

        @ExceptionHandler(ScreeningTimeoutException.class)
        public ResponseEntity<ApiErrorResponse> handleScreeningTimeout(
                        ScreeningTimeoutException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_SANCTIONS_002,
                                ex.getMessage(),
                                request,
                                null);
        }

        // ── Risk Engine exception handlers ────────────────────────────────────────

        @ExceptionHandler(HighRiskBlockException.class)
        public ResponseEntity<ApiErrorResponse> handleHighRiskBlock(
                        HighRiskBlockException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_RISK_001,
                                ex.getMessage(),
                                request,
                                Map.of("fraudScore", ex.getScore(),
                                       "riskTier",   ex.getRiskTier()));
        }

        @ExceptionHandler(VelocityLimitException.class)
        public ResponseEntity<ApiErrorResponse> handleVelocityLimit(
                        VelocityLimitException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_RISK_002,
                                ex.getMessage(),
                                request,
                                Map.of("checkType", ex.getCheckType(),
                                       "pspId",     ex.getPspId()));
        }

        // ── VPA / Account Lookup exception handlers ─────────────────────────────

        @ExceptionHandler(VpaNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleVpaNotFound(
                        VpaNotFoundException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_3001, ex.getMessage(), request, null);
        }

        @ExceptionHandler(VpaDuplicateException.class)
        public ResponseEntity<ApiErrorResponse> handleVpaDuplicate(
                        VpaDuplicateException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_3002, ex.getMessage(), request, null);
        }

        @ExceptionHandler(BeneficiaryTokenExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleTokenExpired(
                        BeneficiaryTokenExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_3003, ex.getMessage(), request, null);
        }

        @ExceptionHandler(BeneficiaryTokenUsedException.class)
        public ResponseEntity<ApiErrorResponse> handleTokenUsed(
                        BeneficiaryTokenUsedException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_3004, ex.getMessage(), request, null);
        }

        // ── Prefunded Pool / Liquidity exception handlers ───────────────────────

        @ExceptionHandler(InsufficientPoolBalanceException.class)
        public ResponseEntity<ApiErrorResponse> handleInsufficientPoolBalance(
                        InsufficientPoolBalanceException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_4001,
                                ex.getMessage(),
                                request,
                                Map.of("pspId", ex.getPspId(),
                                       "requestedAmount", ex.getRequestedAmount(),
                                       "availableBalance", ex.getAvailableBalance()));
        }

        @ExceptionHandler(PoolHoldNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handlePoolHoldNotFound(
                        PoolHoldNotFoundException ex,
                        HttpServletRequest request) {

                return buildResponse(
                                ErrorCatalog.LFP_4002,
                                ex.getMessage(),
                                request,
                                Map.of("txnId", ex.getTxnId()));
        }

        // ── QR Code Service exception handlers (P15) ───────────────────────────

        @ExceptionHandler(QrExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleQrExpired(
                        QrExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_001, ex.getMessage(), request, null);
        }

        @ExceptionHandler(QrAlreadyUsedException.class)
        public ResponseEntity<ApiErrorResponse> handleQrAlreadyUsed(
                        QrAlreadyUsedException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_002, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DuplicateTxnRefException.class)
        public ResponseEntity<ApiErrorResponse> handleDuplicateTxnRef(
                        DuplicateTxnRefException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_003, ex.getMessage(), request, null);
        }

        @ExceptionHandler(MerchantNotActiveException.class)
        public ResponseEntity<ApiErrorResponse> handleMerchantNotActive(
                        MerchantNotActiveException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_004, ex.getMessage(), request, null);
        }

        @ExceptionHandler(QrChecksumException.class)
        public ResponseEntity<ApiErrorResponse> handleQrChecksum(
                        QrChecksumException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_005, ex.getMessage(), request, null);
        }

        @ExceptionHandler(QrNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleQrNotFound(
                        QrNotFoundException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_006, ex.getMessage(), request, null);
        }

        @ExceptionHandler(QrRefundWindowExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleQrRefundWindow(
                        QrRefundWindowExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_QR_007, ex.getMessage(), request, null);
        }

        // ── P16 Bill Payment ──────────────────────────────────────────────────

        @ExceptionHandler(BillNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleBillNotFound(
                        BillNotFoundException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_BILL_001, ex.getMessage(), request, null);
        }

        @ExceptionHandler(BillTokenExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleBillTokenExpired(
                        BillTokenExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_BILL_002, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DuplicateBillPaymentException.class)
        public ResponseEntity<ApiErrorResponse> handleDuplicateBillPayment(
                        DuplicateBillPaymentException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_BILL_003, ex.getMessage(), request, null);
        }

        @ExceptionHandler(BillerTimeoutException.class)
        public ResponseEntity<ApiErrorResponse> handleBillerTimeout(
                        BillerTimeoutException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_BILL_004, ex.getMessage(), request, null);
        }

        // ── P18 Dispute & Refund ──────────────────────────────────────────────

        @ExceptionHandler(DisputeWindowExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleDisputeWindowExpired(
                        DisputeWindowExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_DISPUTE_001, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DisputeTypeInvalidException.class)
        public ResponseEntity<ApiErrorResponse> handleDisputeTypeInvalid(
                        DisputeTypeInvalidException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_DISPUTE_002, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DisputeAlreadyExistsException.class)
        public ResponseEntity<ApiErrorResponse> handleDisputeAlreadyExists(
                        DisputeAlreadyExistsException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_DISPUTE_003, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DisputeNotAuthorizedException.class)
        public ResponseEntity<ApiErrorResponse> handleDisputeNotAuthorized(
                        DisputeNotAuthorizedException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_DISPUTE_004, ex.getMessage(), request, null);
        }

        @ExceptionHandler(DisputeNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleDisputeNotFound(
                        DisputeNotFoundException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_DISPUTE_001, ex.getMessage(), request, null);
        }

        // ── P17 Cross-border ──────────────────────────────────────────────────

        @ExceptionHandler(FxQuoteExpiredException.class)
        public ResponseEntity<ApiErrorResponse> handleFxQuoteExpired(
                        FxQuoteExpiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_CB_001, ex.getMessage(), request, null);
        }

        @ExceptionHandler(CorridorNotAvailableException.class)
        public ResponseEntity<ApiErrorResponse> handleCorridorNotAvailable(
                        CorridorNotAvailableException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_CB_002, ex.getMessage(), request, null);
        }

        @ExceptionHandler(PurposeCodeRequiredException.class)
        public ResponseEntity<ApiErrorResponse> handlePurposeCodeRequired(
                        PurposeCodeRequiredException ex, HttpServletRequest request) {
                return buildResponse(ErrorCatalog.LFP_CB_003, ex.getMessage(), request, null);
        }

        private ResponseEntity<ApiErrorResponse> buildResponse(
                        ErrorCatalog catalog,
                        String message,
                        HttpServletRequest request,
                        Map<String, Object> details) {

                ApiErrorResponse body = new ApiErrorResponse();
                body.setTimestamp(java.time.LocalDateTime.now());
                body.setStatus(catalog.getHttpStatus().value());
                body.setError(catalog.getError());
                body.setErrorCode(catalog.getErrorCode());
                body.setCategory(catalog.getCategory().name());
                body.setLayer(catalog.getLayer().name());
                body.setPhase(catalog.getPhase().name());
                body.setRetryable(catalog.isRetryable());
                body.setMessage(message != null ? message : catalog.getDefaultMessage());
                body.setPath(request.getRequestURI());
                body.setRequestId((String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
                body.setTraceId(traceContext.currentTraceId().orElse(null));
                body.setDetails(details);

                return ResponseEntity.status(catalog.getHttpStatus()).body(body);
        }
}
