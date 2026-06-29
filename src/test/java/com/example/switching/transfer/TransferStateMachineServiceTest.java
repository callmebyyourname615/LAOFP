package com.example.switching.transfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.entity.TransferStatusHistoryEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.exception.InvalidTransferStatusTransitionException;
import com.example.switching.transfer.repository.TransferStatusHistoryRepository;
import com.example.switching.transfer.service.TransferStateMachineService;

class TransferStateMachineServiceTest {

    private final TransferStatusHistoryRepository historyRepository =
            mock(TransferStatusHistoryRepository.class);

    private final TransferStateMachineService stateMachine =
            new TransferStateMachineService(historyRepository);

    @Test
    void acceptedCanMoveToReadyForSettlementOrReject() {
        assertTrue(stateMachine.canTransition(TransferStatus.ACCEPTED, TransferStatus.READY_FOR_SETTLEMENT));
        assertTrue(stateMachine.canTransition(TransferStatus.ACCEPTED, TransferStatus.REJECTED));
        assertFalse(stateMachine.canTransition(TransferStatus.ACCEPTED, TransferStatus.SETTLED));
    }

    @Test
    void readyForSettlementCanSettleOrReject() {
        assertTrue(stateMachine.canTransition(TransferStatus.READY_FOR_SETTLEMENT, TransferStatus.SETTLED));
        assertTrue(stateMachine.canTransition(TransferStatus.READY_FOR_SETTLEMENT, TransferStatus.REJECTED));
    }

    @Test
    void settledCanMoveToRefundRequestedButCannotRejectAgain() {
        assertTrue(stateMachine.canTransition(TransferStatus.SETTLED, TransferStatus.REFUND_REQUESTED));
        assertFalse(stateMachine.canTransition(TransferStatus.SETTLED, TransferStatus.REJECTED));
    }

    @Test
    void invalidTransitionThrows() {
        TransferEntity transfer = new TransferEntity();
        transfer.setTransferRef("TRX-SM-001");
        transfer.setStatus(TransferStatus.SETTLED);

        assertThrows(InvalidTransferStatusTransitionException.class,
                () -> stateMachine.transition(transfer, TransferStatus.REJECTED, "TEST"));
    }

    @Test
    void validTransitionUpdatesStatusAndWritesHistory() {
        TransferEntity transfer = new TransferEntity();
        transfer.setTransferRef("TRX-SM-002");
        transfer.setStatus(TransferStatus.READY_FOR_SETTLEMENT);

        stateMachine.transition(transfer, TransferStatus.SETTLED, null);

        assertTrue(transfer.getStatus() == TransferStatus.SETTLED);
        verify(historyRepository).save(any(TransferStatusHistoryEntity.class));
    }
}
