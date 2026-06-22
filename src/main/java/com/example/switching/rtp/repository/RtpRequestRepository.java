package com.example.switching.rtp.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.switching.rtp.entity.RtpRequestEntity;

import jakarta.persistence.LockModeType;

@Repository
public interface RtpRequestRepository extends JpaRepository<RtpRequestEntity, UUID> {

    Optional<RtpRequestEntity> findByPayeeParticipantIdAndRequestCorrelationId(
            String payeeParticipantId,
            String requestCorrelationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RtpRequestEntity request where request.id = :id")
    Optional<RtpRequestEntity> findByIdForUpdate(@Param("id") UUID id);
}
