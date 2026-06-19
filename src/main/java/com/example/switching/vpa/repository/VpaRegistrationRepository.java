package com.example.switching.vpa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.vpa.entity.VpaRegistrationEntity;

public interface VpaRegistrationRepository extends JpaRepository<VpaRegistrationEntity, Long> {

    Optional<VpaRegistrationEntity> findByVpaId(String vpaId);

    /** Active lookup — used by resolve() and duplicate check. */
    Optional<VpaRegistrationEntity> findByVpaTypeAndVpaValueAndStatus(
            String vpaType, String vpaValue, String status);

    boolean existsByVpaTypeAndVpaValueAndStatus(
            String vpaType, String vpaValue, String status);
}
