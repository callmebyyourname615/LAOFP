package com.example.switching.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.example.switching.transfer.dto.CreateTransferResponse;

class TransferResponseContractTest {

    @Test
    void createTransferAccepted_returnsOkPendingForSourceBank() {
        CreateTransferResponse response = new CreateTransferResponse(
                "TRX-TEST",
                "ACCEPTED",
                "Transfer request accepted and queued for dispatch");

        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("OK", response.getResult());
        assertEquals("PENDING", response.getResultDetail());
    }

    @Test
    void createTransferReadyForSettlement_returnsOkOkForSourceBank() {
        CreateTransferResponse response = new CreateTransferResponse(
                "TRX-TEST",
                "READY_FOR_SETTLEMENT",
                "Destination accepted transfer");

        assertEquals("OK", response.getResult());
        assertEquals("OK", response.getResultDetail());
    }

    @Test
    void createTransferRejected_returnsFailedRejectedForSourceBank() {
        CreateTransferResponse response = new CreateTransferResponse(
                "TRX-TEST",
                "REJECTED",
                "Destination rejected transfer");

        assertEquals("FAILED", response.getResult());
        assertEquals("REJECTED", response.getResultDetail());
    }
}
