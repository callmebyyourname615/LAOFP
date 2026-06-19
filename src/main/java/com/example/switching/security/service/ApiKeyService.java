package com.example.switching.security.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.security.dto.ApiKeyCreateRequest;
import com.example.switching.security.dto.ApiKeyResponse;
import com.example.switching.security.entity.ApiKeyEntity;
import com.example.switching.security.enums.ApiKeyRole;
import com.example.switching.security.repository.ApiKeyRepository;
import com.example.switching.security.util.ApiKeyHashUtil;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public List<ApiKeyResponse> list() {
        return apiKeyRepository.findAll().stream()
                .map(k -> toResponse(k, null))
                .toList();
    }

    @Transactional
    public ApiKeyResponse create(ApiKeyCreateRequest request) {
        String plainKey = ApiKeyHashUtil.generate();
        String keyHash  = ApiKeyHashUtil.hash(plainKey);
        String prefix   = ApiKeyHashUtil.prefix(plainKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyValue(keyHash);
        entity.setKeyPrefix(prefix);
        entity.setName(request.getName());
        entity.setRole(ApiKeyRole.valueOf(request.getRole().toUpperCase()));
        entity.setBankCode(request.getBankCode());
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(request.getExpiresAt());

        apiKeyRepository.save(entity);

        // Return plain key once — it will never be retrievable again
        return toResponse(entity, plainKey);
    }

    @Transactional
    public ApiKeyResponse disable(Long id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
        entity.setEnabled(false);
        apiKeyRepository.save(entity);
        return toResponse(entity, null);
    }

    @Transactional
    public ApiKeyResponse rotate(Long id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));

        String plainKey = ApiKeyHashUtil.generate();
        entity.setKeyValue(ApiKeyHashUtil.hash(plainKey));
        entity.setKeyPrefix(ApiKeyHashUtil.prefix(plainKey));
        entity.setLastUsedAt(null);
        apiKeyRepository.save(entity);

        return toResponse(entity, plainKey);
    }

    private ApiKeyResponse toResponse(ApiKeyEntity entity, String plainKey) {
        ApiKeyResponse r = new ApiKeyResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setRole(entity.getRole().name());
        r.setBankCode(entity.getBankCode());
        r.setKeyPrefix(entity.getKeyPrefix());
        r.setPlainKey(plainKey); // null unless just created or rotated
        r.setEnabled(entity.isEnabled());
        r.setCreatedAt(entity.getCreatedAt());
        r.setLastUsedAt(entity.getLastUsedAt());
        r.setExpiresAt(entity.getExpiresAt());
        return r;
    }
}
