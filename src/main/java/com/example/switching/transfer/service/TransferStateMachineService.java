package com.example.switching.transfer.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.entity.TransferStatusHistoryEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.exception.InvalidTransferStatusTransitionException;
import com.example.switching.transfer.repository.TransferStatusHistoryRepository;

@Service
public class TransferStateMachineService {

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(TransferStatus.class);

    static {
        allow(TransferStatus.ACCEPTED, TransferStatus.READY_FOR_SETTLEMENT, TransferStatus.REJECTED);
        allow(TransferStatus.READY_FOR_SETTLEMENT, TransferStatus.SETTLED, TransferStatus.REJECTED);
        allow(TransferStatus.RECEIVED, TransferStatus.SETTLED, TransferStatus.REJECTED,
                TransferStatus.SUCCESS, TransferStatus.FAILED);
        allow(TransferStatus.SETTLED, TransferStatus.REFUND_REQUESTED);
        allow(TransferStatus.SUCCESS, TransferStatus.REFUND_REQUESTED);
        allow(TransferStatus.REFUND_REQUESTED, TransferStatus.REFUNDED);
    }

    private final TransferStatusHistoryRepository historyRepository;

    public TransferStateMachineService(TransferStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void initialize(TransferEntity transfer, TransferStatus status, String reasonCode) {
        transfer.setStatus(status);
        applyStatusTimestamp(transfer, status);
        saveHistory(transfer.getTransferRef(), status, reasonCode);
    }

    public void transition(TransferEntity transfer, TransferStatus toStatus, String reasonCode) {
        TransferStatus fromStatus = transfer.getStatus();
        if (!canTransition(fromStatus, toStatus)) {
            throw new InvalidTransferStatusTransitionException(
                    transfer.getTransferRef(), fromStatus, toStatus);
        }

        transfer.setStatus(toStatus);
        applyStatusTimestamp(transfer, toStatus);
        saveHistory(transfer.getTransferRef(), toStatus, reasonCode);
    }

    private void applyStatusTimestamp(TransferEntity transfer, TransferStatus status) {
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case ACCEPTED -> { if (transfer.getAcceptedAt() == null) transfer.setAcceptedAt(now); }
            case SETTLED, SUCCESS -> { if (transfer.getSettledAt() == null) transfer.setSettledAt(now); }
            case REJECTED, FAILED -> { if (transfer.getRejectedAt() == null) transfer.setRejectedAt(now); }
            default -> { }
        }
    }

    public boolean canTransition(TransferStatus fromStatus, TransferStatus toStatus) {
        if (fromStatus == null || toStatus == null || fromStatus == toStatus) {
            return false;
        }
        return ALLOWED_TRANSITIONS
                .getOrDefault(fromStatus, Set.of())
                .contains(toStatus);
    }

    private static void allow(TransferStatus fromStatus, TransferStatus... toStatuses) {
        ALLOWED_TRANSITIONS.put(fromStatus, EnumSet.copyOf(Set.of(toStatuses)));
    }

    private void saveHistory(String transferRef, TransferStatus status, String reasonCode) {
        TransferStatusHistoryEntity history = new TransferStatusHistoryEntity();
        history.setTransferRef(transferRef);
        history.setStatus(status.name());
        history.setReasonCode(reasonCode);
        history.setCreatedAt(LocalDateTime.now());
        historyRepository.save(history);
    }
}
