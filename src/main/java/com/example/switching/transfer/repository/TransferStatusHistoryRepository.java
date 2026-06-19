package com.example.switching.transfer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.transfer.entity.TransferStatusHistoryEntity;

@Repository
public interface TransferStatusHistoryRepository extends JpaRepository<TransferStatusHistoryEntity, Long> {

    List<TransferStatusHistoryEntity> findByTransferRefOrderByCreatedAtAsc(String transferRef);
    List<TransferStatusHistoryEntity> findAllByTransferRefOrderByIdAsc(String transferRef);
    
}