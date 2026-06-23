package com.example.switching.outbox.service;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.outbox.dto.DispatchTransferCommand;
import com.example.switching.outbox.entity.OutboxEventEntity;
import com.example.switching.outbox.enums.OutboxStatus;
import com.example.switching.outbox.event.OutboxCreatedEvent;
import com.example.switching.outbox.repository.OutboxEventRepository;
import com.example.switching.observability.tracing.TraceContextSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxTransactionService {

    private static final String MESSAGE_TYPE = "TRANSFER_DISPATCH";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TraceContextSupport traceContext;

    public OutboxTransactionService(OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper,
                                    ApplicationEventPublisher eventPublisher,
                                    TraceContextSupport traceContext) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.traceContext = traceContext;
    }

    @Transactional
    public void enqueueTransferDispatch(DispatchTransferCommand command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            LocalDateTime now = LocalDateTime.now();

            OutboxEventEntity event = new OutboxEventEntity();
            event.setTransferRef(command.getTransferRef());
            event.setMessageType(MESSAGE_TYPE);
            event.setPayload(payload);
            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(0);
            event.setCreatedAt(now);
            event.setTraceId(traceContext.currentTraceId().orElse(null));

            OutboxEventEntity saved = outboxEventRepository.save(event);

            // Publish after save — Spring fires this AFTER the outer transaction commits
            // (TransactionalEventListener on the worker listens for AFTER_COMMIT)
            eventPublisher.publishEvent(new OutboxCreatedEvent(saved.getId(), command.getTransferRef()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize dispatch command", ex);
        }
    }
}