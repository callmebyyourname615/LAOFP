package com.example.switching.vpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.vpa.entity.BeneficiaryTokenEntity;

public interface BeneficiaryTokenRepository extends JpaRepository<BeneficiaryTokenEntity, String> {
    // tokenId (String UUID) is the PK — findById / save are sufficient
}
