package com.example.switching.transfer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;
import com.example.switching.transfer.service.TransferSubmissionService;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferSubmissionService createTransferService;

    public TransferController(TransferSubmissionService createTransferService) {
        this.createTransferService = createTransferService;
    }

    @PostMapping
    public CreateTransferResponse create(@RequestBody CreateTransferRequest request) {
        return createTransferService.create(request);
    }
}