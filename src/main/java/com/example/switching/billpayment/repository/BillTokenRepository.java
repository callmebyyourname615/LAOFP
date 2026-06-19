package com.example.switching.billpayment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.billpayment.entity.BillTokenEntity;

public interface BillTokenRepository extends JpaRepository<BillTokenEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE BillTokenEntity t SET t.used = true WHERE t.tokenId = :tokenId")
    int markUsed(@Param("tokenId") Long tokenId);
}
