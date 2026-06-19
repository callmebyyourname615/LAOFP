package com.example.switching.transfer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.transfer.dto.TransferListResponse;
import com.example.switching.transfer.service.TransferListService;

@RestController
public class TransferListController {

    private final TransferListService transferListService;

    public TransferListController(TransferListService transferListService) {
        this.transferListService = transferListService;
    }

    @GetMapping("/api/transfers")
    public ResponseEntity<TransferListResponse> searchTransfers(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "inquiryRef", required = false) String inquiryRef,
            @RequestParam(value = "sourceBank", required = false) String sourceBank,
            @RequestParam(value = "destinationBank", required = false) String destinationBank,
            @RequestParam(value = "limit", required = false) Integer limit) {

        TransferListResponse response = transferListService.search(
                status,
                inquiryRef,
                sourceBank,
                destinationBank,
                limit
        );

        return ResponseEntity.ok(response);
    }
}