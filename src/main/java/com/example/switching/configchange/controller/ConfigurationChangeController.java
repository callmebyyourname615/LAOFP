package com.example.switching.configchange.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.configchange.dto.ConfigurationChangeCreateRequest;
import com.example.switching.configchange.dto.ConfigurationChangeRejectRequest;
import com.example.switching.configchange.dto.ConfigurationChangeResponse;
import com.example.switching.configchange.entity.ConfigurationChangeStatus;
import com.example.switching.configchange.service.ConfigurationChangeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${switching.api.v1-prefix:/v1}/operations/config-changes")
public class ConfigurationChangeController {
    private final ConfigurationChangeService service;

    public ConfigurationChangeController(ConfigurationChangeService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfigurationChangeResponse> list(@RequestParam(defaultValue = "PENDING") ConfigurationChangeStatus status) {
        return service.list(status);
    }

    @PostMapping
    public ConfigurationChangeResponse request(@Valid @RequestBody ConfigurationChangeCreateRequest request,
                                               Principal principal) {
        return service.request(request, principal.getName());
    }

    @PostMapping("/{id}/approve")
    public ConfigurationChangeResponse approve(@PathVariable Long id, Principal principal) {
        return service.approve(id, principal.getName());
    }

    @PostMapping("/{id}/execute")
    public ConfigurationChangeResponse execute(@PathVariable Long id, Principal principal) {
        return service.execute(id, principal.getName());
    }

    @PostMapping("/{id}/reject")
    public ConfigurationChangeResponse reject(@PathVariable Long id,
                                              @Valid @RequestBody ConfigurationChangeRejectRequest request,
                                              Principal principal) {
        return service.reject(id, principal.getName(), request.reason());
    }
}
