package com.example.switching.inquiry.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.MaskingUtil;
import com.example.switching.inquiry.dto.InquiryResponse;
import com.example.switching.inquiry.dto.InquiryStatusHistoryItemResponse;
import com.example.switching.inquiry.entity.InquiryEntity;
import com.example.switching.inquiry.entity.InquiryStatusHistoryEntity;
import com.example.switching.inquiry.exception.InquiryNotFoundException;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.inquiry.repository.InquiryStatusHistoryRepository;

@Service
public class InquiryLookupService {

    private final InquiryRepository inquiryRepository;
    private final InquiryStatusHistoryRepository inquiryStatusHistoryRepository;
    private final AuditLogService auditLogService;

    public InquiryLookupService(InquiryRepository inquiryRepository,
                                InquiryStatusHistoryRepository inquiryStatusHistoryRepository,
                                AuditLogService auditLogService) {
        this.inquiryRepository = inquiryRepository;
        this.inquiryStatusHistoryRepository = inquiryStatusHistoryRepository;
        this.auditLogService = auditLogService;
    }

    public InquiryResponse getByInquiryRef(String inquiryRef) {
        auditLogService.log(
                "INQUIRY_LOOKUP_REQUESTED",
                "INQUIRY",
                inquiryRef,
                "API",
                Map.of("inquiryRef", inquiryRef)
        );

        try {
            InquiryEntity inquiry = inquiryRepository.findByInquiryRef(inquiryRef)
                    .orElseThrow(() -> new InquiryNotFoundException("Inquiry not found: " + inquiryRef));

            List<InquiryStatusHistoryEntity> historyEntities =
                    inquiryStatusHistoryRepository.findByInquiryRefOrderByCreatedAtAsc(inquiryRef);

            List<InquiryStatusHistoryItemResponse> history = historyEntities.stream()
                    .map(item -> new InquiryStatusHistoryItemResponse(
                            item.getStatus(),
                            item.getReasonCode(),
                            item.getCreatedAt() == null ? null : item.getCreatedAt().toString()
                    ))
                    .toList();

            InquiryResponse response = new InquiryResponse();
            response.setInquiryRef(inquiry.getInquiryRef());
            response.setStatus(inquiry.getStatus() == null ? null : inquiry.getStatus().name());
            response.setSourceBank(inquiry.getSourceBank());
            response.setDestinationBank(inquiry.getDestinationBank());
            response.setCreditorAccount(inquiry.getCreditorAccount());
            response.setDestinationAccountName(inquiry.getDestinationAccountName());
            response.setAccountFound(inquiry.getAccountFound());
            response.setBankAvailable(inquiry.getBankAvailable());
            response.setEligibleForTransfer(inquiry.getEligibleForTransfer());
            response.setHistory(history);

            Map<String, Object> auditPayload = new LinkedHashMap<>();
            auditPayload.put("inquiryRef", response.getInquiryRef());
            auditPayload.put("status", response.getStatus());
            auditPayload.put("sourceBank", response.getSourceBank());
            auditPayload.put("destinationBank", response.getDestinationBank());
            auditPayload.put("creditorAccount", MaskingUtil.maskAccount(response.getCreditorAccount()));
            auditPayload.put("accountFound", response.getAccountFound());
            auditPayload.put("bankAvailable", response.getBankAvailable());
            auditPayload.put("eligibleForTransfer", response.getEligibleForTransfer());
            auditPayload.put("historySize", history.size());

            auditLogService.log(
                    "INQUIRY_LOOKUP_RESPONDED",
                    "INQUIRY",
                    inquiryRef,
                    "API",
                    auditPayload
            );

            return response;

        } catch (Exception ex) {
            auditLogService.logError(
                    "INQUIRY_LOOKUP_FAILED",
                    "INQUIRY",
                    inquiryRef,
                    "API",
                    ex
            );
            throw ex;
        }
    }
}