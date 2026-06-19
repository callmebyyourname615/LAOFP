package com.example.switching.transfer.exception;

import com.example.switching.transfer.enums.TransferStatus;

public class InvalidTransferStatusTransitionException extends RuntimeException {

    public InvalidTransferStatusTransitionException(
            String transferRef,
            TransferStatus fromStatus,
            TransferStatus toStatus) {
        super("Invalid transfer status transition for " + transferRef + ": "
                + fromStatus + " -> " + toStatus);
    }
}
