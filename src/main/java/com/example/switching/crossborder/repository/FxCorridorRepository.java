package com.example.switching.crossborder.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.crossborder.entity.FxCorridorEntity;

public interface FxCorridorRepository extends JpaRepository<FxCorridorEntity, Long> {

    List<FxCorridorEntity> findByStatus(String status);

    List<FxCorridorEntity> findBySourceCurrencyAndDestCurrencyAndStatus(
            String sourceCurrency, String destCurrency, String status);
}
