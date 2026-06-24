package com.example.switching.dashboard.participant.controller;

import com.example.switching.dashboard.participant.dto.ParticipantDashboardResponse;
import com.example.switching.dashboard.participant.service.ParticipantDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/participants")
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class ParticipantDashboardController {
    private final ParticipantDashboardService service;
    public ParticipantDashboardController(ParticipantDashboardService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_DASHBOARD_PARTICIPANT','PERM_DASHBOARD_RISK')")
    public ResponseEntity<ParticipantDashboardResponse> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.load());
    }
}
