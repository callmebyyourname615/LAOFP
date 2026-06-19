package com.example.switching.participant.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.participant.dto.ParticipantListResponse;
import com.example.switching.participant.dto.ParticipantResponse;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.exception.ParticipantUnavailableException;
import com.example.switching.participant.repository.ParticipantRepository;

@Service
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    public ParticipantService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public ParticipantListResponse list(String status) {
        List<ParticipantEntity> participants;

        if (StringUtils.hasText(status)) {
            ParticipantStatus parsedStatus = ParticipantStatus.valueOf(normalize(status));
            participants = participantRepository.findByStatusOrderByBankCodeAsc(parsedStatus);
        } else {
            participants = participantRepository.findAllByOrderByBankCodeAsc();
        }

        List<ParticipantResponse> items = participants.stream()
                .map(ParticipantResponse::from)
                .collect(Collectors.toList());

        return new ParticipantListResponse(items);
    }

    @Transactional(readOnly = true)
    public ParticipantResponse getByBankCode(String bankCode) {
        ParticipantEntity entity = findByBankCode(bankCode);
        return ParticipantResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public ParticipantEntity findByBankCode(String bankCode) {
        String normalized = normalize(bankCode);

        return participantRepository.findByBankCode(normalized)
                .orElseThrow(() -> new ParticipantNotFoundException(normalized));
    }

    @Transactional(readOnly = true)
    public ParticipantEntity requireActive(String bankCode) {
        ParticipantEntity participant = findByBankCode(bankCode);

        if (!participant.active()) {
            throw new ParticipantUnavailableException(
                    participant.getBankCode(),
                    participant.getStatus() == null ? null : participant.getStatus().name()
            );
        }

        return participant;
    }

    public String normalize(String bankCode) {
        if (!StringUtils.hasText(bankCode)) {
            throw new IllegalArgumentException("bankCode is required");
        }

        return bankCode.trim().toUpperCase(Locale.ROOT);
    }
}