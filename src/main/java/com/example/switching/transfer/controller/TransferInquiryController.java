package com.example.switching.transfer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.transfer.dto.TransferInquiryResponse;
import com.example.switching.transfer.service.TransferInquiryService;

@RestController
@RequestMapping("/api/transfers")
public class TransferInquiryController {

    private final TransferInquiryService transferInquiryService;

    public TransferInquiryController(TransferInquiryService transferInquiryService) {
        this.transferInquiryService = transferInquiryService;
    }

    @GetMapping("/{transferRef}")
    public TransferInquiryResponse inquire(@PathVariable String transferRef) {
        return transferInquiryService.inquire(transferRef);
    }
}