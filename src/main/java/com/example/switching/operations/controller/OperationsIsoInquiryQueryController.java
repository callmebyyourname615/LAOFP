package com.example.switching.operations.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsIsoInquiryItemResponse;
import com.example.switching.operations.dto.OperationsIsoInquiryListResponse;
import com.example.switching.operations.service.OperationsIsoInquiryQueryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsIsoInquiryQueryController {

    private final OperationsIsoInquiryQueryService isoInquiryQueryService;

    public OperationsIsoInquiryQueryController(
            OperationsIsoInquiryQueryService isoInquiryQueryService
    ) {
        this.isoInquiryQueryService = isoInquiryQueryService;
    }

    @GetMapping("/iso-inquiries")
    public OperationsIsoInquiryListResponse searchIsoInquiries(
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String sourceBank,
            @RequestParam(required = false) String destinationBank,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String instructionId,
            @RequestParam(required = false) String endToEndId,
            @RequestParam(required = false) String creditorAccount,
            @RequestParam(required = false) String usedByTransferRef,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return isoInquiryQueryService.searchIsoInquiries(
                bankCode,
                sourceBank,
                destinationBank,
                status,
                inquiryRef,
                messageId,
                instructionId,
                endToEndId,
                creditorAccount,
                usedByTransferRef,
                expired,
                fromDate,
                toDate,
                limit,
                offset
        );
    }

    @GetMapping("/iso-inquiries/{inquiryRef}")
    public ResponseEntity<OperationsIsoInquiryItemResponse> findIsoInquiry(
            @PathVariable String inquiryRef
    ) {
        return isoInquiryQueryService.findByInquiryRef(inquiryRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    
}
