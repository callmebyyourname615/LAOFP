package com.example.switching.rtp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.rtp.entity.RtpInstallmentScheduleEntity;

@Repository
public interface RtpInstallmentScheduleRepository extends JpaRepository<RtpInstallmentScheduleEntity, UUID> {
    List<RtpInstallmentScheduleEntity> findByRequestIdOrderByInstallmentNumberAsc(UUID requestId);
}
