package com.example.switching.transfer.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.transfer.dto.TransferListItemResponse;
import com.example.switching.transfer.dto.TransferListResponse;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;
import com.example.switching.transfer.exception.InquiryValidationException;
import com.example.switching.transfer.repository.TransferRepository;

@Service
public class TransferListService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final TransferRepository transferRepository;

    public TransferListService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public TransferListResponse search(String status,
                                       String inquiryRef,
                                       String sourceBank,
                                       String destinationBank,
                                       Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        TransferStatus resolvedStatus = resolveStatus(status);

        String resolvedInquiryRef = normalize(inquiryRef);
        String resolvedSourceBank = normalize(sourceBank);
        String resolvedDestinationBank = normalize(destinationBank);

        List<TransferEntity> transfers = transferRepository.searchTransfers(
                resolvedStatus,
                resolvedInquiryRef,
                resolvedSourceBank,
                resolvedDestinationBank,
                PageRequest.of(0, resolvedLimit)
        );

        List<TransferListItemResponse> items = transfers.stream()
                .map(this::toItem)
                .toList();

        return new TransferListResponse(
                items.size(),
                resolvedLimit,
                resolvedStatus == null ? null : resolvedStatus.name(),
                resolvedInquiryRef,
                resolvedSourceBank,
                resolvedDestinationBank,
                items
        );
    }

    private TransferListItemResponse toItem(TransferEntity transfer) {
        return new TransferListItemResponse(
                transfer.getTransferRef(),
                transfer.getInquiryRef(),
                transfer.getStatus() == null ? null : transfer.getStatus().name(),
                transfer.getSourceBank(),
                transfer.getDebtorAccount(),
                transfer.getDestinationBank(),
                transfer.getCreditorAccount(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getReference(),
                transfer.getExternalReference(),
                transfer.getErrorCode(),
                transfer.getErrorMessage()
        );
    }

    private TransferStatus resolveStatus(String status) {
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        try {
            return TransferStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InquiryValidationException("Invalid transfer status: " + status);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}