package com.example.switching.inquiry.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.inquiry.dto.InquiryTransfersResponse;
import com.example.switching.inquiry.service.InquiryTransferQueryService;

@RestController
public class InquiryTransferController {

    private final InquiryTransferQueryService inquiryTransferQueryService;

    public InquiryTransferController(InquiryTransferQueryService inquiryTransferQueryService) {
        this.inquiryTransferQueryService = inquiryTransferQueryService;
    }

    @GetMapping("/api/inquiries/{inquiryRef}/transfers")
    public ResponseEntity<InquiryTransfersResponse> getTransfersByInquiryRef(
            @PathVariable("inquiryRef") String inquiryRef) {

        InquiryTransfersResponse response =
                inquiryTransferQueryService.findTransfersByInquiryRef(inquiryRef);

        return ResponseEntity.ok(response);
    }
}