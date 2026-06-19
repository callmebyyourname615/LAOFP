package com.example.switching.participant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, Long> {

    Optional<ParticipantEntity> findByBankCode(String bankCode);

    boolean existsByBankCode(String bankCode);

    List<ParticipantEntity> findByStatusOrderByBankCodeAsc(ParticipantStatus status);

    List<ParticipantEntity> findAllByOrderByBankCodeAsc();
}