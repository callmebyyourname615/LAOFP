package com.example.switching.reconciliation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.reconciliation.entity.ReconciliationFileEntity;

public interface ReconciliationFileRepository extends JpaRepository<ReconciliationFileEntity, Long> {

    Optional<ReconciliationFileEntity> findByFileRef(String fileRef);

    List<ReconciliationFileEntity> findByReconciliationDateOrderByIdDesc(LocalDate date);

    List<ReconciliationFileEntity> findByStatusOrderByReconciliationDateDescIdDesc(String status);

    List<ReconciliationFileEntity> findBySourceBankAndReconciliationDateOrderByIdDesc(
            String sourceBank, LocalDate date);

    boolean existsByFileRef(String fileRef);
}
