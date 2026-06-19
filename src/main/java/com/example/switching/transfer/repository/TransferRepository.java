package com.example.switching.transfer.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.transfer.entity.TransferEntity;
import com.example.switching.transfer.enums.TransferStatus;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    Optional<TransferEntity> findByTransferRef(String transferRef);

    Optional<TransferEntity> findByInquiryRef(String inquiryRef);

    List<TransferEntity> findAllByInquiryRefOrderByIdAsc(String inquiryRef);

    long countByStatus(TransferStatus status);

    /** Used by settlement batch to collect all SETTLED transfers on a given business date. */
    List<TransferEntity> findByStatusAndBusinessDate(TransferStatus status, LocalDate businessDate);

    List<TransferEntity> findByStatusAndBusinessDateAndSettlementMethod(
            TransferStatus status, LocalDate businessDate, String settlementMethod);

    @Query("""
           select t
             from TransferEntity t
            where (:status is null or t.status = :status)
              and (:inquiryRef is null or t.inquiryRef = :inquiryRef)
              and (:sourceBank is null or upper(t.sourceBank) = upper(cast(:sourceBank as string)))
              and (:destinationBank is null or upper(t.destinationBank) = upper(cast(:destinationBank as string)))
            order by t.id desc
           """)
    List<TransferEntity> searchTransfers(
            @Param("status") TransferStatus status,
            @Param("inquiryRef") String inquiryRef,
            @Param("sourceBank") String sourceBank,
            @Param("destinationBank") String destinationBank,
            Pageable pageable
    );
}
