package com.example.switching.participant.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.participant.dto.CreateParticipantRequest;
import com.example.switching.participant.dto.ParticipantListResponse;
import com.example.switching.participant.dto.ParticipantResponse;
import com.example.switching.participant.dto.UpdateParticipantRequest;
import com.example.switching.participant.service.ParticipantManagementService;
import com.example.switching.participant.service.ParticipantService;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {

    private final ParticipantService participantService;
    private final ParticipantManagementService participantManagementService;

    public ParticipantController(ParticipantService participantService,
                                 ParticipantManagementService participantManagementService) {
        this.participantService = participantService;
        this.participantManagementService = participantManagementService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ParticipantListResponse list(
            @RequestParam(required = false) String status) {
        return participantService.list(status);
    }

    @GetMapping("/{bankCode}")
    public ParticipantResponse getByBankCode(
            @PathVariable String bankCode) {
        return participantService.getByBankCode(bankCode);
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ParticipantResponse> create(
            @RequestBody CreateParticipantRequest request) {
        ParticipantResponse response = participantManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{bankCode}")
    public ParticipantResponse update(
            @PathVariable String bankCode,
            @RequestBody UpdateParticipantRequest request) {
        return participantManagementService.update(bankCode, request);
    }
}
