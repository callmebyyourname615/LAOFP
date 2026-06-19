package com.example.switching.idempotency.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.switching.idempotency.entity.IdempotencyRecordEntity;
import com.example.switching.idempotency.exception.IdempotencyConflictException;
import com.example.switching.idempotency.repository.IdempotencyRecordRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.repository.TransferRepository;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransferRepository transferRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository,
                              TransferRepository transferRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transferRepository = transferRepository;
    }

    public Optional<TransferEntity> findExistingTransfer(String channelId,
                                                         String idempotencyKey,
                                                         String requestHash) {
        if (channelId == null || channelId.isBlank() ||
            idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyRecordEntity> recordOptional =
                idempotencyRecordRepository.findByChannelIdAndIdempotencyKey(channelId, idempotencyKey);

        if (recordOptional.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecordEntity record = recordOptional.get();

        if (record.getRequestHash() != null && !record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key already used with different payload: " + idempotencyKey
            );
        }

        if (record.getTransferRef() == null || record.getTransferRef().isBlank()) {
            return Optional.empty();
        }

        return transferRepository.findByTransferRef(record.getTransferRef());
    }

    public IdempotencyRecordEntity saveNew(String channelId,
                                           String idempotencyKey,
                                           String requestHash,
                                           String transferRef,
                                           String status) {
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setChannelId(channelId);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setRequestHash(requestHash);
        entity.setTransferRef(transferRef);
        entity.setStatus(status);
        return idempotencyRecordRepository.save(entity);
    }

    public void updateStatus(String channelId, String idempotencyKey, String status) {
        if (channelId == null || channelId.isBlank() ||
            idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        idempotencyRecordRepository
                .findByChannelIdAndIdempotencyKey(channelId, idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(status);
                    idempotencyRecordRepository.save(record);
                });
    }
}