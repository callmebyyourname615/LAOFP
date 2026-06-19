package com.example.switching.idempotency.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.idempotency.entity.IdempotencyRecordEntity;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {

    Optional<IdempotencyRecordEntity> findByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);
}