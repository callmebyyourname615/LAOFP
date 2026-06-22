package com.example.switching.promotion.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.switching.promotion.entity.PromotionEntity;

import jakarta.persistence.LockModeType;

@Repository
public interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {
    Optional<PromotionEntity> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select promotion from PromotionEntity promotion where promotion.id = :id")
    Optional<PromotionEntity> findByIdForUpdate(@Param("id") UUID id);
}
