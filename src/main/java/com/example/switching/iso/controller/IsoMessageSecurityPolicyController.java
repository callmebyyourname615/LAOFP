package com.example.switching.iso.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.iso.dto.IsoMessageSecurityPolicyResponse;
import com.example.switching.iso.service.IsoMessageSecurityPolicyService;

@RestController
public class IsoMessageSecurityPolicyController {

    private final IsoMessageSecurityPolicyService isoMessageSecurityPolicyService;

    public IsoMessageSecurityPolicyController(IsoMessageSecurityPolicyService isoMessageSecurityPolicyService) {
        this.isoMessageSecurityPolicyService = isoMessageSecurityPolicyService;
    }

    @GetMapping("/api/iso-messages/{messageKey}/security-policy")
    public IsoMessageSecurityPolicyResponse getPolicy(
            @PathVariable String messageKey
    ) {
        return isoMessageSecurityPolicyService.getPolicy(messageKey);
    }
}