package com.example.switching.bauactivation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/bau")
@ConditionalOnProperty(name="switching.phase81.bau.enabled",havingValue="true")
public class BauActivationController {
    private final BauActivationService service;
    public BauActivationController(BauActivationService service){this.service=service;}
    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('READINESS_VIEWER','READINESS_OPERATOR','CHANGE_MANAGER')")
    public ResponseEntity<BauActivationStatus> status(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());}
}
