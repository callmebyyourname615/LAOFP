package com.example.switching.crossborder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.crossborder.entity.FxQuoteEntity;

public interface FxQuoteRepository extends JpaRepository<FxQuoteEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FxQuoteEntity q SET q.used = true WHERE q.quoteId = :quoteId")
    int markUsed(@Param("quoteId") Long quoteId);
}
