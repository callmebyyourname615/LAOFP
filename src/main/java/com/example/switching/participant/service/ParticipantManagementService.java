package com.example.switching.participant.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.participant.dto.CreateParticipantRequest;
import com.example.switching.participant.dto.ParticipantResponse;
import com.example.switching.participant.dto.UpdateParticipantRequest;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.enums.ParticipantType;
import com.example.switching.participant.exception.ParticipantAlreadyExistsException;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.repository.ParticipantRepository;

@Service
public class ParticipantManagementService {

    private final ParticipantRepository participantRepository;

    public ParticipantManagementService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Transactional
    public ParticipantResponse create(CreateParticipantRequest request) {
        requireField(request.getBankCode(), "bankCode");
        requireField(request.getBankName(), "bankName");
        requireField(request.getCountry(), "country");
        requireField(request.getCurrency(), "currency");

        String bankCode = normalizeCode(request.getBankCode());

        if (participantRepository.findByBankCode(bankCode).isPresent()) {
            throw new ParticipantAlreadyExistsException(bankCode);
        }

        ParticipantStatus status = parseStatus(request.getStatus(), ParticipantStatus.INACTIVE);
        if (status == ParticipantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "new participants must be certified and activated through the four-eyes configuration workflow");
        }
        ParticipantType participantType = parseType(request.getParticipantType(), ParticipantType.DIRECT);

        ParticipantEntity entity = new ParticipantEntity();
        entity.setBankCode(bankCode);
        entity.setBankName(request.getBankName().trim());
        entity.setStatus(status);
        entity.setParticipantType(participantType);
        entity.setCountry(request.getCountry().trim().toUpperCase(Locale.ROOT));
        entity.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));

        ParticipantEntity saved = participantRepository.save(entity);

        return ParticipantResponse.from(saved);
    }

    @Transactional
    public ParticipantResponse update(String bankCode, UpdateParticipantRequest request) {
        String normalizedCode = normalizeCode(bankCode);

        ParticipantEntity entity = participantRepository.findByBankCode(normalizedCode)
                .orElseThrow(() -> new ParticipantNotFoundException(normalizedCode));

        if (request.getBankName() != null) {
            if (StringUtils.hasText(request.getBankName())) {
                entity.setBankName(request.getBankName().trim());
            }
        }

        if (StringUtils.hasText(request.getStatus())) {
            ParticipantStatus requestedStatus = parseStatus(request.getStatus(), entity.getStatus());
            if (requestedStatus != entity.getStatus()) {
                throw new IllegalStateException(
                        "participant status changes require the four-eyes configuration workflow");
            }
        }

        if (request.getCountry() != null) {
            if (StringUtils.hasText(request.getCountry())) {
                entity.setCountry(request.getCountry().trim().toUpperCase(Locale.ROOT));
            }
        }

        if (request.getCurrency() != null) {
            if (StringUtils.hasText(request.getCurrency())) {
                entity.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
            }
        }

        ParticipantEntity saved = participantRepository.save(entity);

        return ParticipantResponse.from(saved);
    }

    private ParticipantStatus parseStatus(String raw, ParticipantStatus defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }

        try {
            return ParticipantStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid participant status: " + raw + ". Valid values: ACTIVE, INACTIVE, MAINTENANCE");
        }
    }

    private ParticipantType parseType(String raw, ParticipantType defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }

        try {
            return ParticipantType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid participant type: " + raw + ". Valid values: DIRECT, INDIRECT");
        }
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("bankCode is required");
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireField(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
