package com.example.switching.transfer.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.entity.AuditLogEntity;
import com.example.switching.audit.repository.AuditLogRepository;
import com.example.switching.inquiry.entity.InquiryEntity;
import com.example.switching.inquiry.repository.InquiryRepository;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.repository.IsoMessageRepository;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.transfer.dto.TransferTraceAuditItemResponse;
import com.example.switching.transfer.dto.TransferTraceHistoryItemResponse;
import com.example.switching.transfer.dto.TransferTraceIsoMessageItemResponse;
import com.example.switching.transfer.dto.TransferTraceOutboxItemResponse;
import com.example.switching.transfer.dto.TransferTraceResponse;
import com.example.switching.transfer.dto.TransferTraceTimelineItemResponse;
import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.entity.TransferStatusHistoryEntity;
import com.example.switching.transfer.exception.TransferNotFoundException;
import com.example.switching.transfer.repository.TransferRepository;
import com.example.switching.transfer.repository.TransferStatusHistoryRepository;

@Service
public class TransferTraceService {

    private static final String REFERENCE_TYPE_TRANSFER = "TRANSFER";

    private final TransferRepository transferRepository;
    private final InquiryRepository inquiryRepository;
    private final TransferStatusHistoryRepository transferStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final IsoMessageRepository isoMessageRepository;

    public TransferTraceService(
            TransferRepository transferRepository,
            InquiryRepository inquiryRepository,
            TransferStatusHistoryRepository transferStatusHistoryRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository,
            IsoMessageRepository isoMessageRepository
    ) {
        this.transferRepository = transferRepository;
        this.inquiryRepository = inquiryRepository;
        this.transferStatusHistoryRepository = transferStatusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.isoMessageRepository = isoMessageRepository;
    }

    @Transactional(readOnly = true)
    public TransferTraceResponse getTrace(String transferRef) {
        TransferEntity transfer = transferRepository.findByTransferRef(transferRef)
                .orElseThrow(() -> new TransferNotFoundException(transferRef));

        InquiryEntity inquiry = null;
        if (StringUtils.hasText(transfer.getInquiryRef())) {
            inquiry = inquiryRepository.findByInquiryRef(transfer.getInquiryRef()).orElse(null);
        }

        List<TransferTraceHistoryItemResponse> transferHistory =
                buildTransferHistory(transfer.getTransferRef());

        List<TransferTraceOutboxItemResponse> outboxEvents =
                buildOutboxEvents(transfer.getTransferRef());

        List<TransferTraceAuditItemResponse> auditEvents =
                buildAuditEvents(transfer.getTransferRef());

        List<TransferTraceIsoMessageItemResponse> isoMessages =
                buildIsoMessages(transfer.getTransferRef());

        List<TransferTraceTimelineItemResponse> timeline =
                buildTimeline(
                        transferHistory,
                        outboxEvents,
                        auditEvents,
                        isoMessages
                );

        TransferTraceResponse response = new TransferTraceResponse();

        response.setTransferRef(transfer.getTransferRef());
        response.setCurrentStatus(transfer.getStatus() == null ? null : transfer.getStatus().name());

        response.setSourceBank(transfer.getSourceBank());
        response.setDestinationBank(transfer.getDestinationBank());
        response.setDebtorAccount(transfer.getDebtorAccount());
        response.setCreditorAccount(transfer.getCreditorAccount());
        response.setAmount(transfer.getAmount());
        response.setCurrency(transfer.getCurrency());

        response.setInquiryRef(transfer.getInquiryRef());
        response.setExternalReference(transfer.getExternalReference());
        response.setReference(transfer.getReference());
        response.setErrorCode(transfer.getErrorCode());
        response.setErrorMessage(transfer.getErrorMessage());

        if (inquiry != null) {
            response.setInquiryStatus(inquiry.getStatus() == null ? null : inquiry.getStatus().name());
            response.setInquiryAccountFound(inquiry.getAccountFound());
            response.setInquiryBankAvailable(inquiry.getBankAvailable());
            response.setInquiryEligibleForTransfer(inquiry.getEligibleForTransfer());
            response.setDestinationAccountName(inquiry.getDestinationAccountName());
        }

        response.setTransferHistory(transferHistory);
        response.setOutboxEvents(outboxEvents);
        response.setAuditEvents(auditEvents);
        response.setIsoMessages(isoMessages);
        response.setTimeline(timeline);

        return response;
    }

    private List<TransferTraceHistoryItemResponse> buildTransferHistory(String transferRef) {
        if (!StringUtils.hasText(transferRef)) {
            return Collections.emptyList();
        }

        return transferStatusHistoryRepository.findByTransferRefOrderByCreatedAtAsc(transferRef)
                .stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());
    }

    private TransferTraceHistoryItemResponse toHistoryItem(TransferStatusHistoryEntity entity) {
        TransferTraceHistoryItemResponse item = new TransferTraceHistoryItemResponse();
        item.setStatus(entity.getStatus());
        item.setReasonCode(entity.getReasonCode());
        item.setCreatedAt(entity.getCreatedAt());
        return item;
    }

    private List<TransferTraceOutboxItemResponse> buildOutboxEvents(String transferRef) {
        if (!StringUtils.hasText(transferRef)) {
            return Collections.emptyList();
        }

        return outboxEventRepository.findByTransferRefOrderByCreatedAtAsc(transferRef)
                .stream()
                .map(this::toOutboxItem)
                .collect(Collectors.toList());
    }

    private TransferTraceOutboxItemResponse toOutboxItem(OutboxEventEntity entity) {
        TransferTraceOutboxItemResponse item = new TransferTraceOutboxItemResponse();
        item.setOutboxEventId(entity.getId());
        item.setMessageType(entity.getMessageType());
        item.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        item.setRetryCount(entity.getRetryCount());
        item.setCreatedAt(entity.getCreatedAt());
        return item;
    }

    private List<TransferTraceAuditItemResponse> buildAuditEvents(String transferRef) {
        if (!StringUtils.hasText(transferRef)) {
            return Collections.emptyList();
        }

        return auditLogRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                        REFERENCE_TYPE_TRANSFER,
                        transferRef
                )
                .stream()
                .map(this::toAuditItem)
                .collect(Collectors.toList());
    }

    private TransferTraceAuditItemResponse toAuditItem(AuditLogEntity entity) {
        TransferTraceAuditItemResponse item = new TransferTraceAuditItemResponse();

        item.setAuditLogId(entity.getId());
        item.setEventType(entity.getEventType());

        /*
         * DTO เดิมใช้ entityType/entityRef/sourceSystem
         * แต่ table/entity audit ใช้ referenceType/referenceId/actor
         */
        item.setEntityType(entity.getReferenceType());
        item.setEntityRef(entity.getReferenceId());
        item.setSourceSystem(entity.getActor());

        item.setCreatedAt(entity.getCreatedAt());

        return item;
    }

    private List<TransferTraceIsoMessageItemResponse> buildIsoMessages(String transferRef) {
        if (!StringUtils.hasText(transferRef)) {
            return Collections.emptyList();
        }

        return isoMessageRepository.findByTransferRefOrderByCreatedAtAsc(transferRef)
                .stream()
                .map(this::toIsoMessageItem)
                .collect(Collectors.toList());
    }

    private TransferTraceIsoMessageItemResponse toIsoMessageItem(IsoMessageEntity entity) {
        TransferTraceIsoMessageItemResponse item = new TransferTraceIsoMessageItemResponse();

        item.setId(entity.getId());
        item.setCorrelationRef(entity.getCorrelationRef());
        item.setInquiryRef(entity.getInquiryRef());
        item.setTransferRef(entity.getTransferRef());
        item.setMessageId(entity.getMessageId());
        item.setEndToEndId(entity.getEndToEndId());

        item.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        item.setDirection(entity.getDirection() == null ? null : entity.getDirection().name());
        item.setSecurityStatus(entity.getSecurityStatus() == null ? null : entity.getSecurityStatus().name());
        item.setValidationStatus(entity.getValidationStatus() == null ? null : entity.getValidationStatus().name());

        item.setErrorCode(entity.getErrorCode());
        item.setErrorMessage(entity.getErrorMessage());
        item.setCreatedAt(entity.getCreatedAt());

        return item;
    }

    private List<TransferTraceTimelineItemResponse> buildTimeline(
            List<TransferTraceHistoryItemResponse> transferHistory,
            List<TransferTraceOutboxItemResponse> outboxEvents,
            List<TransferTraceAuditItemResponse> auditEvents,
            List<TransferTraceIsoMessageItemResponse> isoMessages
    ) {
        List<TransferTraceTimelineItemResponse> timeline = new ArrayList<>();

        for (TransferTraceHistoryItemResponse history : safeList(transferHistory)) {
            timeline.add(new TransferTraceTimelineItemResponse(
                    history.getCreatedAt(),
                    "TRANSFER_STATUS",
                    "TRANSFER",
                    history.getStatus(),
                    null,
                    null,
                    null,
                    "Transfer status changed to " + history.getStatus(),
                    history.getReasonCode() == null
                            ? null
                            : "reasonCode=" + history.getReasonCode()
            ));
        }

        for (TransferTraceIsoMessageItemResponse iso : safeList(isoMessages)) {
            timeline.add(new TransferTraceTimelineItemResponse(
                    iso.getCreatedAt(),
                    "ISO_MESSAGE",
                    "ISO",
                    iso.getSecurityStatus(),
                    iso.getMessageType(),
                    iso.getDirection(),
                    iso.getMessageId(),
                    iso.getMessageType() + " " + iso.getDirection(),
                    "messageId="
                            + iso.getMessageId()
                            + ", validationStatus="
                            + iso.getValidationStatus()
            ));
        }

        for (TransferTraceOutboxItemResponse outbox : safeList(outboxEvents)) {
            timeline.add(new TransferTraceTimelineItemResponse(
                    outbox.getCreatedAt(),
                    "OUTBOX_EVENT",
                    "OUTBOX",
                    outbox.getStatus(),
                    outbox.getMessageType(),
                    null,
                    outbox.getOutboxEventId() == null ? null : String.valueOf(outbox.getOutboxEventId()),
                    "Outbox event " + outbox.getStatus(),
                    "retryCount=" + outbox.getRetryCount()
            ));
        }

        for (TransferTraceAuditItemResponse audit : safeList(auditEvents)) {
            timeline.add(new TransferTraceTimelineItemResponse(
                    audit.getCreatedAt(),
                    audit.getEventType(),
                    audit.getSourceSystem(),
                    null,
                    null,
                    null,
                    audit.getAuditLogId() == null ? null : String.valueOf(audit.getAuditLogId()),
                    audit.getEventType(),
                    "auditLogId=" + audit.getAuditLogId()
            ));
        }

        timeline.sort(
                Comparator.comparing(
                        TransferTraceTimelineItemResponse::getTimestamp,
                        Comparator.nullsLast(LocalDateTime::compareTo)
                )
        );

        return timeline;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}