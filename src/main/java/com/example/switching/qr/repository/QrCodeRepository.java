package com.example.switching.qr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.qr.entity.QrCodeEntity;

public interface QrCodeRepository extends JpaRepository<QrCodeEntity, Long> {

    Optional<QrCodeEntity> findByQrId(String qrId);

    boolean existsByTxnRef(String txnRef);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE QrCodeEntity q SET q.used = true WHERE q.qrId = :qrId")
    int markUsed(@Param("qrId") String qrId);
}
