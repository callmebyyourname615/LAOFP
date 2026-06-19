package com.example.switching.security.breakglass.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.security.breakglass.dto.PrivilegedAccessRequest;
import com.example.switching.security.breakglass.dto.PrivilegedAccessResponse;
import com.example.switching.security.breakglass.entity.PrivilegedAccessStatus;
import com.example.switching.security.breakglass.service.PrivilegedAccessService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operations/break-glass")
public class PrivilegedAccessController {
    private final PrivilegedAccessService service;

    public PrivilegedAccessController(PrivilegedAccessService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrivilegedAccessResponse> list(@RequestParam(defaultValue = "ACTIVE") PrivilegedAccessStatus status) {
        return service.list(status);
    }

    @PostMapping
    public PrivilegedAccessResponse request(@Valid @RequestBody PrivilegedAccessRequest request, Principal principal) {
        return service.request(request, principal.getName());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PrivilegedAccessResponse> approve(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(service.approve(id, principal.getName()));
    }

    @PostMapping("/{id}/revoke")
    public PrivilegedAccessResponse revoke(@PathVariable Long id, Principal principal) {
        return service.revoke(id, principal.getName());
    }
}
