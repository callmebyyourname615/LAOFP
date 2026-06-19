package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsGenerateRoutesForBankRequest;
import com.example.switching.operations.dto.OperationsGenerateRoutesForBankResponse;
import com.example.switching.operations.service.OperationsGenerateRoutesForBankService;

@RestController
@RequestMapping("/api/operations")
public class OperationsGenerateRoutesForBankController {

    private final OperationsGenerateRoutesForBankService generateRoutesForBankService;

    public OperationsGenerateRoutesForBankController(
            OperationsGenerateRoutesForBankService generateRoutesForBankService
    ) {
        this.generateRoutesForBankService = generateRoutesForBankService;
    }

    @PostMapping("/bank-onboarding/generate-routes")
    public OperationsGenerateRoutesForBankResponse generateRoutesForBank(
            @RequestBody OperationsGenerateRoutesForBankRequest request
    ) {
        return generateRoutesForBankService.generateForBank(request);
    }
}
