package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsBankStatusListResponse;
import com.example.switching.operations.service.OperationsBankStatusService;

@RestController
@RequestMapping("/api/operations")
public class OperationsBankStatusController {

    private final OperationsBankStatusService operationsBankStatusService;

    public OperationsBankStatusController(
            OperationsBankStatusService operationsBankStatusService
    ) {
        this.operationsBankStatusService = operationsBankStatusService;
    }

    @GetMapping("/bank-status")
    public OperationsBankStatusListResponse getBankStatus(
            @RequestParam(required = false) String bankCode
    ) {
        return operationsBankStatusService.getBankStatus(bankCode);
    }
}