package com.example.switching.inquiry.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.switching.inquiry.entity.InquiryEntity;
import com.example.switching.inquiry.enums.InquiryStatus;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, Long> {

    Optional<InquiryEntity> findByInquiryRef(String inquiryRef);

    long countByStatus(InquiryStatus status);
}