package com.example.switching.certification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.certification.entity.ParticipantCertificationEntity;
import com.example.switching.certification.entity.ParticipantCertificationResult;

public interface ParticipantCertificationRepository extends JpaRepository<ParticipantCertificationEntity, Long> {
    Optional<ParticipantCertificationEntity> findFirstByBankCodeOrderByExecutedAtDescIdDesc(String bankCode);
    List<ParticipantCertificationEntity> findTop100ByBankCodeOrderByExecutedAtDesc(String bankCode);
}
