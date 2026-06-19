package com.example.switching.billpayment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.billpayment.entity.BillerEntity;

public interface BillerRepository extends JpaRepository<BillerEntity, Long> {

    List<BillerEntity> findByStatus(String status);

    Optional<BillerEntity> findByBillerCode(String billerCode);
}
