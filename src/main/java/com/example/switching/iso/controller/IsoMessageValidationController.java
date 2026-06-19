package com.example.switching.iso.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.iso.dto.IsoMessageValidationActionResponse;
import com.example.switching.iso.service.IsoMessageValidationService;

@RestController
public class IsoMessageValidationController {

    private final IsoMessageValidationService isoMessageValidationService;

    public IsoMessageValidationController(IsoMessageValidationService isoMessageValidationService) {
        this.isoMessageValidationService = isoMessageValidationService;
    }

    @PostMapping("/api/iso-messages/{messageKey}/validate")
    public IsoMessageValidationActionResponse validate(
            @PathVariable String messageKey
    ) {
        return isoMessageValidationService.validate(messageKey);
    }
}