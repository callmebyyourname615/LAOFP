package com.example.switching.inquiry.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.switching.inquiry.dto.CreateInquiryRequest;
import com.example.switching.inquiry.dto.CreateInquiryResponse;
import com.example.switching.inquiry.dto.InquiryResponse;
import com.example.switching.inquiry.service.CreateInquiryService;
import com.example.switching.inquiry.service.InquiryLookupService;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final CreateInquiryService createInquiryService;
    private final InquiryLookupService inquiryLookupService;

    public InquiryController(CreateInquiryService createInquiryService,
                             InquiryLookupService inquiryLookupService) {
        this.createInquiryService = createInquiryService;
        this.inquiryLookupService = inquiryLookupService;
    }

    @PostMapping
    public ResponseEntity<CreateInquiryResponse> create(@RequestBody CreateInquiryRequest request) {
        return ResponseEntity.ok(createInquiryService.create(request));
    }

    @GetMapping("/{inquiryRef}")
    public ResponseEntity<InquiryResponse> getByInquiryRef(@PathVariable String inquiryRef) {
        return ResponseEntity.ok(inquiryLookupService.getByInquiryRef(inquiryRef));
    }
}
