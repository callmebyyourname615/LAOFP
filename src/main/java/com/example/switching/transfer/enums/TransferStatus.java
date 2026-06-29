package com.example.switching.transfer.enums;

public enum TransferStatus {
    ACCEPTED,
    READY_FOR_SETTLEMENT,
    SETTLED,
    REJECTED,
    REFUND_REQUESTED,
    REFUNDED,

    /**
     * Legacy alias retained for older rows and API clients.
     * New transfers should use ACCEPTED.
     */
    RECEIVED,

    /**
     * Legacy alias retained for older rows and API clients.
     * New successful transfers should use SETTLED.
     */
    SUCCESS,

    /**
     * Legacy alias retained for older rows and API clients.
     * New failed transfers should use REJECTED.
     */
    FAILED
}
