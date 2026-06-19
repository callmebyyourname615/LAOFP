package com.example.switching.operations.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.operations.dto.OperationsBankOnboardingRequest;
import com.example.switching.operations.dto.OperationsBankOnboardingResponse;
import com.example.switching.operations.service.OperationsBankOnboardingService;

@RestController
@RequestMapping("/api/operations")
public class OperationsBankOnboardingController {

    private final OperationsBankOnboardingService onboardingService;

    public OperationsBankOnboardingController(
            OperationsBankOnboardingService onboardingService
    ) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/bank-onboarding")
    public OperationsBankOnboardingResponse onboardBank(
            @RequestBody OperationsBankOnboardingRequest request
    ) {
        return onboardingService.onboardBank(request);
    }
}