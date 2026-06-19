package com.example.switching.compliance.legalhold.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.compliance.legalhold.dto.LegalHoldRequest;
import com.example.switching.compliance.legalhold.dto.LegalHoldResponse;
import com.example.switching.compliance.legalhold.entity.LegalHoldStatus;
import com.example.switching.compliance.legalhold.service.LegalHoldService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operations/legal-holds")
@Validated
public class LegalHoldController {
    private final LegalHoldService service;

    public LegalHoldController(LegalHoldService service) {
        this.service = service;
    }

    @GetMapping
    public List<LegalHoldResponse> list(@RequestParam(defaultValue = "ACTIVE") LegalHoldStatus status) {
        return service.list(status);
    }

    @PostMapping
    public LegalHoldResponse request(@Valid @RequestBody LegalHoldRequest request, Principal principal) {
        return service.request(request, principal.getName());
    }

    @PostMapping("/{id}/approve")
    public LegalHoldResponse approve(@PathVariable Long id, Principal principal) {
        return service.approve(id, principal.getName());
    }

    @PostMapping("/{id}/request-release")
    public LegalHoldResponse requestRelease(@PathVariable Long id, Principal principal) {
        return service.requestRelease(id, principal.getName());
    }

    @PostMapping("/{id}/approve-release")
    public LegalHoldResponse approveRelease(@PathVariable Long id, Principal principal) {
        return service.approveRelease(id, principal.getName());
    }
}
