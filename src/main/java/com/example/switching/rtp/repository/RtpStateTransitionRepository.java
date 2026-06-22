package com.example.switching.rtp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.rtp.entity.RtpStateTransitionEntity;

@Repository
public interface RtpStateTransitionRepository extends JpaRepository<RtpStateTransitionEntity, UUID> {
    List<RtpStateTransitionEntity> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
