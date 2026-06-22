package com.example.switching.inquiry.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.switching.inquiry.entity.InquiryEntity;
import com.example.switching.inquiry.enums.InquiryStatus;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, Long> {

    @Query(value = """
            SELECT q.* FROM inquiry_lookup l
            JOIN inquiries q ON q.inquiry_ref = l.inquiry_ref
                            AND q.business_date = l.business_date
            WHERE l.inquiry_ref = :inquiryRef
            """, nativeQuery = true)
    Optional<InquiryEntity> findByInquiryRef(@Param("inquiryRef") String inquiryRef);

    long countByStatus(InquiryStatus status);
}
