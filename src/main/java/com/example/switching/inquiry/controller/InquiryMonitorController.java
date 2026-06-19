package com.example.switching.inquiry.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.inquiry.dto.InquiryMonitorListResponse;
import com.example.switching.inquiry.service.InquiryMonitorQueryService;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryMonitorController {

    private final InquiryMonitorQueryService inquiryMonitorQueryService;

    public InquiryMonitorController(InquiryMonitorQueryService inquiryMonitorQueryService) {
        this.inquiryMonitorQueryService = inquiryMonitorQueryService;
    }

    @GetMapping
    public InquiryMonitorListResponse searchInquiries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceBank,
            @RequestParam(required = false) String destinationBank,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String clientInquiryId,
            @RequestParam(required = false) String creditorAccount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return inquiryMonitorQueryService.searchInquiries(
                status,
                sourceBank,
                destinationBank,
                inquiryRef,
                clientInquiryId,
                creditorAccount,
                currency,
                fromDate,
                toDate,
                limit,
                offset
        );
    }
}