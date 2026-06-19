package com.example.switching.iso.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.iso.dto.IsoMessageDetailResponse;
import com.example.switching.iso.dto.IsoMessageItemResponse;
import com.example.switching.iso.dto.IsoMessageListResponse;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.exception.IsoMessageNotFoundException;
import com.example.switching.iso.repository.IsoMessageRepository;

@Service
public class IsoMessageQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final IsoMessageRepository isoMessageRepository;

    public IsoMessageQueryService(IsoMessageRepository isoMessageRepository) {
        this.isoMessageRepository = isoMessageRepository;
    }

    @Transactional(readOnly = true)
    public IsoMessageListResponse search(
            String messageType,
            String direction,
            String correlationRef,
            String inquiryRef,
            String transferRef,
            String endToEndId,
            Integer limit
    ) {
        int safeLimit = sanitizeLimit(limit);

        IsoMessageType parsedMessageType = parseMessageType(messageType);
        IsoMessageDirection parsedDirection = parseDirection(direction);

        List<IsoMessageItemResponse> items = isoMessageRepository
                .search(
                        parsedMessageType,
                        parsedDirection,
                        normalize(correlationRef),
                        normalize(inquiryRef),
                        normalize(transferRef),
                        normalize(endToEndId),
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        IsoMessageListResponse response = new IsoMessageListResponse();
        response.setCount(items.size());
        response.setLimit(safeLimit);
        response.setMessageType(normalize(messageType));
        response.setDirection(normalize(direction));
        response.setCorrelationRef(normalize(correlationRef));
        response.setInquiryRef(normalize(inquiryRef));
        response.setTransferRef(normalize(transferRef));
        response.setEndToEndId(normalize(endToEndId));
        response.setItems(items);

        return response;
    }

    @Transactional(readOnly = true)
    public IsoMessageDetailResponse getDetail(String messageKey) {
        IsoMessageEntity entity = findByIdOrMessageId(messageKey);
        return toDetailResponse(entity);
    }

    @Transactional(readOnly = true)
    public IsoMessageEntity findEntityByIdOrMessageId(String messageKey) {
        return findByIdOrMessageId(messageKey);
    }

    private IsoMessageEntity findByIdOrMessageId(String messageKey) {
        if (!StringUtils.hasText(messageKey)) {
            throw new IsoMessageNotFoundException("empty");
        }

        String key = messageKey.trim();

        if (isNumeric(key)) {
            Long id = Long.valueOf(key);
            return isoMessageRepository.findById(id)
                    .orElseThrow(() -> new IsoMessageNotFoundException(key));
        }

        return isoMessageRepository.findByMessageId(key)
                .orElseThrow(() -> new IsoMessageNotFoundException(key));
    }

    private IsoMessageItemResponse toItemResponse(IsoMessageEntity entity) {
        IsoMessageItemResponse response = new IsoMessageItemResponse();

        response.setId(entity.getId());
        response.setCorrelationRef(entity.getCorrelationRef());
        response.setInquiryRef(entity.getInquiryRef());
        response.setTransferRef(entity.getTransferRef());
        response.setEndToEndId(entity.getEndToEndId());
        response.setMessageId(entity.getMessageId());
        response.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        response.setDirection(entity.getDirection() == null ? null : entity.getDirection().name());
        response.setSecurityStatus(entity.getSecurityStatus() == null ? null : entity.getSecurityStatus().name());
        response.setValidationStatus(entity.getValidationStatus() == null ? null : entity.getValidationStatus().name());
        response.setErrorCode(entity.getErrorCode());
        response.setErrorMessage(entity.getErrorMessage());
        response.setCreatedAt(entity.getCreatedAt());

        return response;
    }

    private IsoMessageDetailResponse toDetailResponse(IsoMessageEntity entity) {
        IsoMessageDetailResponse response = new IsoMessageDetailResponse();

        response.setId(entity.getId());
        response.setCorrelationRef(entity.getCorrelationRef());
        response.setInquiryRef(entity.getInquiryRef());
        response.setTransferRef(entity.getTransferRef());
        response.setEndToEndId(entity.getEndToEndId());
        response.setMessageId(entity.getMessageId());
        response.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        response.setDirection(entity.getDirection() == null ? null : entity.getDirection().name());
        response.setSecurityStatus(entity.getSecurityStatus() == null ? null : entity.getSecurityStatus().name());
        response.setValidationStatus(entity.getValidationStatus() == null ? null : entity.getValidationStatus().name());
        response.setPlainPayload(entity.getPlainPayload());
        response.setEncryptedPayload(entity.getEncryptedPayload());
        response.setErrorCode(entity.getErrorCode());
        response.setErrorMessage(entity.getErrorMessage());
        response.setCreatedAt(entity.getCreatedAt());

        return response;
    }

    private IsoMessageType parseMessageType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return IsoMessageType.valueOf(value.trim().toUpperCase());
    }

    private IsoMessageDirection parseDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return IsoMessageDirection.valueOf(value.trim().toUpperCase());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isNumeric(String value) {
        return value.matches("\\d+");
    }
}