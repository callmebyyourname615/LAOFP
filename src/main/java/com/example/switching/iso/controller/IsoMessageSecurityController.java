package com.example.switching.iso.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.iso.dto.IsoMessageSecurityActionResponse;
import com.example.switching.iso.service.IsoMessageSecurityService;

@RestController
public class IsoMessageSecurityController {

    private final IsoMessageSecurityService isoMessageSecurityService;

    public IsoMessageSecurityController(IsoMessageSecurityService isoMessageSecurityService) {
        this.isoMessageSecurityService = isoMessageSecurityService;
    }

    @PostMapping("/api/iso-messages/{id}/encrypt")
    public ResponseEntity<IsoMessageSecurityActionResponse> encryptIsoMessage(
            @PathVariable("id") Long id) {

        IsoMessageSecurityActionResponse response = isoMessageSecurityService.encrypt(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/iso-messages/{id}/decrypt")
    public ResponseEntity<IsoMessageSecurityActionResponse> decryptIsoMessage(
            @PathVariable("id") Long id) {

        IsoMessageSecurityActionResponse response = isoMessageSecurityService.decrypt(id);
        return ResponseEntity.ok(response);
    }
}