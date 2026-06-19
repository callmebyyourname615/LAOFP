package com.example.switching.iso.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.iso.dto.IsoMessageValidationActionResponse;
import com.example.switching.iso.dto.IsoXmlValidationResult;
import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoValidationStatus;
import com.example.switching.iso.parser.IsoXmlValidator;
import com.example.switching.iso.repository.IsoMessageRepository;

@Service
public class IsoMessageValidationService {

    private final IsoMessageQueryService isoMessageQueryService;
    private final IsoMessageRepository isoMessageRepository;
    private final IsoXmlValidator isoXmlValidator;

    public IsoMessageValidationService(
            IsoMessageQueryService isoMessageQueryService,
            IsoMessageRepository isoMessageRepository,
            IsoXmlValidator isoXmlValidator
    ) {
        this.isoMessageQueryService = isoMessageQueryService;
        this.isoMessageRepository = isoMessageRepository;
        this.isoXmlValidator = isoXmlValidator;
    }

    @Transactional
    public IsoMessageValidationActionResponse validate(String messageKey) {
        IsoMessageEntity entity = isoMessageQueryService.findEntityByIdOrMessageId(messageKey);

        IsoXmlValidationResult result;

        if (!StringUtils.hasText(entity.getPlainPayload())) {
            result = IsoXmlValidationResult.invalid(
                    "ISO-VAL-PLAIN-001",
                    "Plain payload is required for XML validation. Decrypt the message first if it is encrypted.",
                    entity.getMessageType() == null ? null : entity.getMessageType().name()
            );
        } else {
            result = isoXmlValidator.validate(entity.getPlainPayload(), entity.getMessageType(), entity.getId());
        }

        if (result.isValid()) {
            entity.setValidationStatus(IsoValidationStatus.VALID);
            entity.setErrorCode(null);
            entity.setErrorMessage(null);
        } else {
            entity.setValidationStatus(IsoValidationStatus.INVALID);
            entity.setErrorCode(result.getErrorCode());
            entity.setErrorMessage(result.getErrorMessage());
        }

        IsoMessageEntity saved = isoMessageRepository.save(entity);

        return toResponse(saved, result);
    }

    private IsoMessageValidationActionResponse toResponse(
            IsoMessageEntity entity,
            IsoXmlValidationResult result
    ) {
        IsoMessageValidationActionResponse response = new IsoMessageValidationActionResponse();

        response.setId(entity.getId());
        response.setMessageId(entity.getMessageId());
        response.setTransferRef(entity.getTransferRef());
        response.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        response.setDirection(entity.getDirection() == null ? null : entity.getDirection().name());
        response.setValidationStatus(entity.getValidationStatus() == null ? null : entity.getValidationStatus().name());

        response.setValid(result.isValid());
        response.setDetectedMessageType(result.getDetectedMessageType());
        response.setDetectedMessageId(result.getMessageId());
        response.setDetectedEndToEndId(result.getEndToEndId());
        response.setDetectedTransactionStatus(result.getTransactionStatus());
        response.setErrorCode(result.getErrorCode());
        response.setErrorMessage(result.getErrorMessage());
        response.setValidatedAt(LocalDateTime.now());

        return response;
    }
}