package com.example.switching.inquiry.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.inquiry.entity.InquiryStatusHistoryEntity;

public interface InquiryStatusHistoryRepository extends JpaRepository<InquiryStatusHistoryEntity, Long> {

    List<InquiryStatusHistoryEntity> findByInquiryRefOrderByCreatedAtAsc(String inquiryRef);
}
