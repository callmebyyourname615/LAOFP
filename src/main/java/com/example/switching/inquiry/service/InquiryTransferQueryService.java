package com.example.switching.inquiry.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.inquiry.dto.InquiryTransferItemResponse;
import com.example.switching.inquiry.dto.InquiryTransfersResponse;
import com.example.switching.inquiry.exception.InquiryNotFoundException;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.repository.TransferRepository;

@Service
public class InquiryTransferQueryService {

    private final InquiryRepository inquiryRepository;
    private final TransferRepository transferRepository;

    public InquiryTransferQueryService(InquiryRepository inquiryRepository,
                                       TransferRepository transferRepository) {
        this.inquiryRepository = inquiryRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public InquiryTransfersResponse findTransfersByInquiryRef(String inquiryRef) {
        String resolvedInquiryRef = requireInquiryRef(inquiryRef);

        boolean inquiryExists = inquiryRepository.findByInquiryRef(resolvedInquiryRef).isPresent();
        if (!inquiryExists) {
            throw new InquiryNotFoundException("Inquiry not found: " + resolvedInquiryRef);
        }

        List<TransferEntity> transfers =
                transferRepository.findAllByInquiryRefOrderByIdAsc(resolvedInquiryRef);

        List<InquiryTransferItemResponse> items = transfers.stream()
                .map(this::toItemResponse)
                .toList();

        return new InquiryTransfersResponse(
                resolvedInquiryRef,
                !items.isEmpty(),
                items.size(),
                items
        );
    }

    private InquiryTransferItemResponse toItemResponse(TransferEntity transfer) {
        return new InquiryTransferItemResponse(
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

    private String requireInquiryRef(String inquiryRef) {
        if (inquiryRef == null || inquiryRef.isBlank()) {
            throw new InquiryNotFoundException("Inquiry not found");
        }
        return inquiryRef.trim();
    }
}