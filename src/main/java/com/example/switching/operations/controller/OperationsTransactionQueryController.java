package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsTransactionListResponse;
import com.example.switching.operations.service.OperationsTransactionQueryService;

@RestController
@RequestMapping("/api/operations")
public class OperationsTransactionQueryController {

    private final OperationsTransactionQueryService transactionQueryService;

    public OperationsTransactionQueryController(
            OperationsTransactionQueryService transactionQueryService
    ) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping("/transactions")
    public OperationsTransactionListResponse searchTransactions(
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) String sourceBank,
            @RequestParam(required = false) String destinationBank,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String transferRef,
            @RequestParam(required = false) String inquiryRef,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return transactionQueryService.searchTransactions(
                bankCode,
                sourceBank,
                destinationBank,
                status,
                transferRef,
                inquiryRef,
                fromDate,
                toDate,
                limit,
                offset
        );
    }
}