package com.example.switching.outbox.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.outbox.dto.OutboxEventItemResponse;
import com.example.switching.outbox.dto.OutboxEventListResponse;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.repository.OutboxEventRepository;

@Service
public class OutboxEventQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventQueryService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public OutboxEventListResponse search(String status,
                                          String transferRef,
                                          Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        OutboxStatus resolvedStatus = resolveStatus(status);
        String resolvedTransferRef = normalize(transferRef);

        List<OutboxEventEntity> events = outboxEventRepository.searchOutboxEvents(
                resolvedStatus,
                resolvedTransferRef,
                PageRequest.of(0, resolvedLimit)
        );

        List<OutboxEventItemResponse> items = events.stream()
                .map(this::toItem)
                .toList();

        return new OutboxEventListResponse(
                items.size(),
                resolvedLimit,
                resolvedStatus == null ? null : resolvedStatus.name(),
                resolvedTransferRef,
                items
        );
    }

    private OutboxEventItemResponse toItem(OutboxEventEntity event) {
        return new OutboxEventItemResponse(
                event.getId(),
                event.getTransferRef(),
                event.getMessageType(),
                event.getStatus() == null ? null : event.getStatus().name(),
                event.getRetryCount(),
                event.getCreatedAt(),
                null
        );
    }

    private OutboxStatus resolveStatus(String status) {
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        try {
            return OutboxStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid outbox status: " + status);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
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