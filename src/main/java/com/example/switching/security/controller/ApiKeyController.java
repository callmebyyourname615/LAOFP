package com.example.switching.security.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.security.dto.ApiKeyCreateRequest;
import com.example.switching.security.dto.ApiKeyResponse;
import com.example.switching.security.service.ApiKeyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> list() {
        return ResponseEntity.ok(apiKeyService.list());
    }

    @PostMapping
    public ResponseEntity<ApiKeyResponse> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        ApiKeyResponse response = apiKeyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiKeyResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.disable(id));
    }

    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiKeyResponse> rotate(@PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.rotate(id));
    }
}
