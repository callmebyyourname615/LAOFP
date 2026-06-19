package com.example.switching.iso.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.switching.iso.entity.IsoMessageEntity;
import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;

public interface IsoMessageRepository extends JpaRepository<IsoMessageEntity, Long> {

    Optional<IsoMessageEntity> findByMessageId(String messageId);

    List<IsoMessageEntity> findByTransferRefOrderByCreatedAtAsc(String transferRef);

    @Query("""
            SELECT i
            FROM IsoMessageEntity i
            WHERE (:messageType IS NULL OR i.messageType = :messageType)
              AND (:direction IS NULL OR i.direction = :direction)
              AND (:correlationRef IS NULL OR i.correlationRef = :correlationRef)
              AND (:inquiryRef IS NULL OR i.inquiryRef = :inquiryRef)
              AND (:transferRef IS NULL OR i.transferRef = :transferRef)
              AND (:endToEndId IS NULL OR i.endToEndId = :endToEndId)
            ORDER BY i.createdAt DESC
            """)
    List<IsoMessageEntity> search(
            IsoMessageType messageType,
            IsoMessageDirection direction,
            String correlationRef,
            String inquiryRef,
            String transferRef,
            String endToEndId,
            Pageable pageable
    );
}