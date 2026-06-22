package com.example.switching.rtp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.rtp.entity.RtpAuthorisationEntity;

@Repository
public interface RtpAuthorisationRepository extends JpaRepository<RtpAuthorisationEntity, UUID> {
    List<RtpAuthorisationEntity> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
