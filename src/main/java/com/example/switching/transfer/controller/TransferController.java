package com.example.switching.transfer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.transfer.dto.CreateTransferRequest;
import com.example.switching.transfer.dto.CreateTransferResponse;
import com.example.switching.transfer.service.CreateTransferService;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final CreateTransferService createTransferService;

    public TransferController(CreateTransferService createTransferService) {
        this.createTransferService = createTransferService;
    }

    @PostMapping
    public CreateTransferResponse create(@RequestBody CreateTransferRequest request) {
        return createTransferService.create(request);
    }
}