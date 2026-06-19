package com.example.switching.transfer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.transfer.dto.TransferTraceResponse;
import com.example.switching.transfer.service.TransferTraceService;

@RestController
public class TransferTraceController {

    private final TransferTraceService transferTraceService;

    public TransferTraceController(TransferTraceService transferTraceService) {
        this.transferTraceService = transferTraceService;
    }

    @GetMapping("/api/transfers/{transferRef}/trace")
    public ResponseEntity<TransferTraceResponse> getTransferTrace(
            @PathVariable("transferRef") String transferRef) {

        TransferTraceResponse response = transferTraceService.getTrace(transferRef);
        return ResponseEntity.ok(response);
    }
}