package com.example.switching.operations.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsTransferTraceResponse;
import com.example.switching.operations.service.OperationsTransferTraceService;

@RestController
public class OperationsTransferTraceController {

    private final OperationsTransferTraceService transferTraceService;

    public OperationsTransferTraceController(OperationsTransferTraceService transferTraceService) {
        this.transferTraceService = transferTraceService;
    }

    @GetMapping("/api/operations/transfers/{transferRef}/trace")
    public ResponseEntity<OperationsTransferTraceResponse> getTransferTrace(
            @PathVariable String transferRef
    ) {
        return transferTraceService.findTraceByTransferRef(transferRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
