package com.example.switching.transfer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.common.util.MaskingUtil;
import com.example.switching.transfer.dto.TransferInquiryResponse;
import com.example.switching.transfer.dto.TransferStatusHistoryItemResponse;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.entity.TransferStatusHistoryEntity;
import com.example.switching.transfer.exception.TransferNotFoundException;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.repository.TransferStatusHistoryRepository;

@Service
public class TransferInquiryService {

    private final TransferRepository transferRepository;
    private final TransferStatusHistoryRepository transferStatusHistoryRepository;
    private final AuditLogService auditLogService;

    public TransferInquiryService(TransferRepository transferRepository,
                                  TransferStatusHistoryRepository transferStatusHistoryRepository,
                                  AuditLogService auditLogService) {
        this.transferRepository = transferRepository;
        this.transferStatusHistoryRepository = transferStatusHistoryRepository;
        this.auditLogService = auditLogService;
    }

    public TransferInquiryResponse inquire(String transferRef) {
        auditLogService.log(
                "TRANSFER_INQUIRY_REQUESTED",
                "TRANSFER",
                transferRef,
                "API",
                Map.of("transferRef", transferRef)
        );

        try {
            TransferEntity transfer = transferRepository.findByTransferRef(transferRef)
                    .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferRef));

            List<TransferStatusHistoryEntity> historyEntities =
                    transferStatusHistoryRepository.findByTransferRefOrderByCreatedAtAsc(transferRef);

            List<TransferStatusHistoryItemResponse> history = historyEntities.stream()
                    .map(item -> new TransferStatusHistoryItemResponse(
                            item.getStatus(),
                            item.getReasonCode(),
                            item.getCreatedAt() == null ? null : item.getCreatedAt().toString()
                    ))
                    .toList();

            TransferInquiryResponse response = toResponse(transfer, history);

            Map<String, Object> auditPayload = new LinkedHashMap<>();
            auditPayload.put("transferRef", response.getTransferRef());
            auditPayload.put("status", response.getStatus());
            auditPayload.put("currentStatus", response.getCurrentStatus());
            auditPayload.put("sourceBank", response.getSourceBank());
            auditPayload.put("destinationBank", response.getDestinationBank());
            auditPayload.put("debtorAccount", MaskingUtil.maskAccount(response.getDebtorAccount()));
            auditPayload.put("creditorAccount", MaskingUtil.maskAccount(response.getCreditorAccount()));
            auditPayload.put("amount", response.getAmount());
            auditPayload.put("currency", response.getCurrency());
            auditPayload.put("inquiryRef", response.getInquiryRef());
            auditPayload.put("historySize", history.size());

            auditLogService.log(
                    "TRANSFER_INQUIRY_RESPONDED",
                    "TRANSFER",
                    transferRef,
                    "API",
                    auditPayload
            );

            return response;

        } catch (Exception ex) {
            auditLogService.logError(
                    "TRANSFER_INQUIRY_FAILED",
                    "TRANSFER",
                    transferRef,
                    "API",
                    ex
            );
            throw ex;
        }
    }

    private TransferInquiryResponse toResponse(
            TransferEntity transfer,
            List<TransferStatusHistoryItemResponse> history
    ) {
        String status = transfer.getStatus() == null ? null : transfer.getStatus().name();

        TransferInquiryResponse response = new TransferInquiryResponse();
        response.setTransferRef(transfer.getTransferRef());

        // Keep original field and add currentStatus for trace/operations-style clients.
        response.setStatus(status);
        response.setCurrentStatus(status);
        response.setResult(mapResult(status));
        response.setResultDetail(mapResultDetail(status));

        response.setSourceBank(transfer.getSourceBank());
        response.setDebtorAccount(transfer.getDebtorAccount());
        response.setDestinationBank(transfer.getDestinationBank());
        response.setCreditorAccount(transfer.getCreditorAccount());

        response.setAmount(transfer.getAmount());
        response.setCurrency(transfer.getCurrency());

        response.setInquiryRef(transfer.getInquiryRef());
        response.setChannelId(transfer.getChannelId());
        response.setRouteCode(transfer.getRouteCode());
        response.setConnectorName(transfer.getConnectorName());
        response.setExternalReference(transfer.getExternalReference());
        response.setReference(transfer.getReference());

        response.setErrorCode(transfer.getErrorCode());
        response.setErrorMessage(transfer.getErrorMessage());

        response.setCreatedAt(transfer.getCreatedAt());
        response.setUpdatedAt(transfer.getUpdatedAt());

        response.setHistory(history);
        return response;
    }

    private String mapResult(String status) {
        if ("REJECTED".equals(status) || "FAILED".equals(status)) {
            return "FAILED";
        }
        return "OK";
    }

    private String mapResultDetail(String status) {
        if ("ACCEPTED".equals(status)) {
            return "PENDING";
        }
        if ("READY_FOR_SETTLEMENT".equals(status) || "SETTLED".equals(status)) {
            return "OK";
        }
        if ("REJECTED".equals(status)) {
            return "REJECTED";
        }
        if ("FAILED".equals(status)) {
            return "FAILED";
        }
        return status;
    }
}
