package com.example.switching.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCatalog {

        REQ_001(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "REQ-001",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Request validation failed"),
        REQ_002(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "REQ-002",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "Malformed JSON request"),
        REQ_003(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED",
                        "REQ-003",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "HTTP method not allowed"),
        REQ_004(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE",
                        "REQ-004",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "Unsupported media type"),

        INQ_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "INQ-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.INQUIRY,
                        ErrorPhase.LOOKUP_INQUIRY,
                        false,
                        "Inquiry not found"),
        INQ_002(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "INQ-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.TRANSFER,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Inquiry validation failed"),
        INQ_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "INQ-003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.TRANSFER,
                        ErrorPhase.CREATE_TRANSFER,
                        false,
                        "Inquiry already used by transfer"),

        TRF_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "TRF-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.TRANSFER,
                        ErrorPhase.READ_RESOURCE,
                        false,
                        "Transfer not found"),
        TRF_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "TRF-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.TRANSFER,
                        ErrorPhase.CREATE_TRANSFER,
                        false,
                        "Idempotency conflict"),

        OUT_001(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "OUT-001",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.PARSE_PAYLOAD,
                        false,
                        "Outbox payload parse failed"),
        OUT_002(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "OUT-002",
                        ErrorCategory.CORE,
                        ErrorLayer.WORKER,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "Outbox worker processing failed"),

        NET_001(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "NET-001",
                        ErrorCategory.NETWORK,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "Downstream connection failed"),
        NET_002(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "NET-002",
                        ErrorCategory.NETWORK,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "Downstream timeout"),
        NET_003(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "NET-003",
                        ErrorCategory.NETWORK,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "DNS resolution failed"),
        NET_004(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "NET-004",
                        ErrorCategory.NETWORK,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "TLS or SSL handshake failed"),

        EXT_001(
                        HttpStatus.BAD_GATEWAY,
                        "BAD_GATEWAY",
                        "EXT-001",
                        ErrorCategory.DOWNSTREAM,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "Downstream bank rejected transfer"),
        EXT_002(
                        HttpStatus.BAD_GATEWAY,
                        "BAD_GATEWAY",
                        "EXT-002",
                        ErrorCategory.DOWNSTREAM,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "Downstream bank returned invalid response"),

        INF_DB_001(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "INF-DB-001",
                        ErrorCategory.INFRASTRUCTURE,
                        ErrorLayer.DATABASE,
                        ErrorPhase.WRITE_DATABASE,
                        true,
                        "Database write failed"),
        INF_DB_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "INF-DB-002",
                        ErrorCategory.INFRASTRUCTURE,
                        ErrorLayer.DATABASE,
                        ErrorPhase.WRITE_DATABASE,
                        false,
                        "Database unique constraint violation"),
        INF_SER_001(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "INF-SER-001",
                        ErrorCategory.INFRASTRUCTURE,
                        ErrorLayer.SYSTEM,
                        ErrorPhase.PARSE_PAYLOAD,
                        false,
                        "Serialization or deserialization failed"),
        OUT_003(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "OUT-003",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "Outbox stuck processing recovered"),
        OUT_004(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "OUT-004",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "Outbox event cannot be manually retried"),
        OUT_005(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "OUT-005",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "Outbox event not found"),
        ISO_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "ISO-001",
                        ErrorCategory.CORE,
                        ErrorLayer.ISO,
                        ErrorPhase.READ_RESOURCE,
                        false,
                        "ISO message not found"),
        ISO_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "ISO-002",
                        ErrorCategory.CORE,
                        ErrorLayer.ISO,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "ISO message invalid state"),
        ISO_003(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "ISO-003",
                        ErrorCategory.INFRASTRUCTURE,
                        ErrorLayer.ISO,
                        ErrorPhase.UNKNOWN,
                        false,
                        "ISO message crypto operation failed"),

        PRT_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "PRT-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.ROUTING,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Participant not found"),
        PRT_002(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "PRT-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.ROUTING,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Participant is not ACTIVE"),
        RTE_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "RTE-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.ROUTING,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Routing rule not found for this bank pair and message type"),
        RTE_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "RTE-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.ROUTING,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Routing rule already exists for this route code"),
        CON_001(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "CON-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Connector config not found or unavailable"),
        CON_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "CON-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.CONNECTOR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Connector config already exists for this connector name"),
        PRT_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "PRT-003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.ROUTING,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Participant already exists for this bank code"),
        LFP_2001(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "LFP-2001",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "Invalid OAuth token"),
        LFP_2002(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "LFP-2002",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "Invalid mTLS client certificate"),
        LFP_2003(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "LFP-2003",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "Request signature invalid"),
        LFP_2004(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "LFP-2004",
                        ErrorCategory.REQUEST,
                        ErrorLayer.API,
                        ErrorPhase.RECEIVE_REQUEST,
                        false,
                        "PSP participant is suspended"),
        LFP_FPRE_001(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "LFP-FPRE-001",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "FPRE maximum retry attempts reached"),
        LFP_FPRE_002(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "LFP-FPRE-002",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        false,
                        "FPRE auto-reversal failed"),
        LFP_FPRE_003(
                        HttpStatus.ACCEPTED,
                        "ACCEPTED",
                        "LFP-FPRE-003",
                        ErrorCategory.CORE,
                        ErrorLayer.OUTBOX,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "FPRE ambiguous state unresolved"),

        // ── AML / CFT ─────────────────────────────────────────────────────────────
        LFP_SANCTIONS_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-SANCTIONS-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.COMPLIANCE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Transaction blocked: sanctions list match"),
        LFP_SANCTIONS_002(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE",
                        "LFP-SANCTIONS-002",
                        ErrorCategory.INFRASTRUCTURE,
                        ErrorLayer.COMPLIANCE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Sanctions screening timed out"),

        // ── VPA / Account Lookup ──────────────────────────────────────────────────
        LFP_3001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "LFP-3001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.VPA,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "VPA not found or inactive"),
        LFP_3002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "LFP-3002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.VPA,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "An active VPA with the same type and value already exists"),
        LFP_3003(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-3003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.VPA,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Beneficiary token has expired"),
        LFP_3004(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-3004",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.VPA,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Beneficiary token has already been used"),

        // ── Prefunded Pool / Liquidity ────────────────────────────────────────────
        LFP_4001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-4001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.LIQUIDITY,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Insufficient pool balance for this PSP"),
        LFP_4002(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "LFP-4002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.LIQUIDITY,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Pool hold record not found"),

        // ── Risk Engine ───────────────────────────────────────────────────────────
        LFP_RISK_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-RISK-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RISK,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Transaction blocked: high fraud risk score"),
        LFP_RISK_002(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "TOO_MANY_REQUESTS",
                        "LFP-RISK-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RISK,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Velocity limit exceeded"),

        // ── QR Code Service (P15) ────────────────────────────────────────────────
        LFP_QR_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-QR-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "QR code has expired"),
        LFP_QR_002(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-QR-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "QR code has already been used"),
        LFP_QR_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "LFP-QR-003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "A QR code with the same transaction reference already exists"),
        LFP_QR_004(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-QR-004",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Merchant is not active"),
        LFP_QR_005(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-QR-005",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "QR code CRC-16 checksum verification failed"),
        LFP_QR_006(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "LFP-QR-006",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "QR code not found"),
        LFP_QR_007(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-QR-007",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.QR,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "QR refund window has expired (max 30 days)"),

        // ── P16 Bill Payment ──────────────────────────────────────────────────

        LFP_BILL_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "LFP-6001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.BILL,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Bill not found"),

        LFP_BILL_002(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-6002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.BILL,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Bill token has expired or already been used"),

        LFP_BILL_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "LFP-6003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.BILL,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Duplicate bill payment within 24 hours"),

        LFP_BILL_004(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "GATEWAY_TIMEOUT",
                        "LFP-6004",
                        ErrorCategory.DOWNSTREAM,
                        ErrorLayer.BILL,
                        ErrorPhase.DISPATCH_TRANSFER,
                        true,
                        "Biller API did not respond within the timeout window"),

        // ── P18 Dispute & Refund ──────────────────────────────────────────────

        LFP_DISPUTE_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-9001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.DISPUTE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Dispute window has expired (max 90 days)"),

        LFP_DISPUTE_002(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "LFP-9002",
                        ErrorCategory.REQUEST,
                        ErrorLayer.DISPUTE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "Invalid dispute type"),

        LFP_DISPUTE_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "LFP-9003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.DISPUTE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "An active dispute already exists for this transaction"),

        LFP_DISPUTE_004(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "LFP-9004",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.DISPUTE,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "PSP is not authorized to act on this dispute"),

        // ── P17 Cross-border ──────────────────────────────────────────────────

        LFP_CB_001(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-CB-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.CROSSBORDER,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "FX quote has expired or already been used"),

        LFP_CB_002(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-CB-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.CROSSBORDER,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "FX corridor is not available"),

        LFP_CB_003(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "LFP-CB-003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.CROSSBORDER,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "purposeCode and sourceOfFunds are required for large cross-border transfers"),

        // ── Phase II Request-to-Pay ─────────────────────────────────────────

        RTP_001(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "RTP-001",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RTP,
                        ErrorPhase.READ_RESOURCE,
                        false,
                        "RTP request not found"),

        RTP_002(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "RTP-002",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RTP,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "RTP idempotency key was reused with a different payload"),

        RTP_003(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        "RTP-003",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RTP,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "RTP state transition is not allowed"),

        RTP_004(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "RTP-004",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RTP,
                        ErrorPhase.READ_RESOURCE,
                        false,
                        "Caller is not permitted to access this RTP request"),

        RTP_005(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "UNPROCESSABLE_ENTITY",
                        "RTP-005",
                        ErrorCategory.BUSINESS,
                        ErrorLayer.RTP,
                        ErrorPhase.VALIDATE_REQUEST,
                        false,
                        "RTP expiry is invalid"),

        SYS_001(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR",
                        "SYS-001",
                        ErrorCategory.UNKNOWN,
                        ErrorLayer.SYSTEM,
                        ErrorPhase.UNKNOWN,
                        false,
                        "Internal server error");

        private final HttpStatus httpStatus;
        private final String error;
        private final String errorCode;
        private final ErrorCategory category;
        private final ErrorLayer layer;
        private final ErrorPhase phase;
        private final boolean retryable;
        private final String defaultMessage;

        ErrorCatalog(HttpStatus httpStatus,
                        String error,
                        String errorCode,
                        ErrorCategory category,
                        ErrorLayer layer,
                        ErrorPhase phase,
                        boolean retryable,
                        String defaultMessage) {
                this.httpStatus = httpStatus;
                this.error = error;
                this.errorCode = errorCode;
                this.category = category;
                this.layer = layer;
                this.phase = phase;
                this.retryable = retryable;
                this.defaultMessage = defaultMessage;
        }

        public HttpStatus getHttpStatus() {
                return httpStatus;
        }

        public String getError() {
                return error;
        }

        public String getErrorCode() {
                return errorCode;
        }

        public ErrorCategory getCategory() {
                return category;
        }

        public ErrorLayer getLayer() {
                return layer;
        }

        public ErrorPhase getPhase() {
                return phase;
        }

        public boolean isRetryable() {
                return retryable;
        }

        public String getDefaultMessage() {
                return defaultMessage;
        }
}
