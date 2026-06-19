package com.example.switching.certification.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.certification.dto.ParticipantCertificationRecordRequest;
import com.example.switching.certification.dto.ParticipantCertificationResponse;
import com.example.switching.certification.service.ParticipantCertificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operations/participant-certifications")
public class ParticipantCertificationController {
    private final ParticipantCertificationService service;

    public ParticipantCertificationController(ParticipantCertificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParticipantCertificationResponse> list(@RequestParam String bankCode) {
        return service.list(bankCode);
    }

    @PostMapping
    public ParticipantCertificationResponse record(@Valid @RequestBody ParticipantCertificationRecordRequest request,
                                                   Principal principal) {
        return service.record(request, principal.getName());
    }
}
