package com.example.switching.usermgmt.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.switching.usermgmt.dto.*;
import com.example.switching.usermgmt.service.AuthenticationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class AuthController {
    private final AuthenticationService authentication;
    public AuthController(AuthenticationService authentication) { this.authentication = authentication; }

    @PostMapping("/login") public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authentication.login(request.username(), request.password()));
    }
    @PostMapping("/mfa") public ResponseEntity<AuthResponse> mfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(authentication.verifyMfa(request.mfaToken(), request.totpCode()));
    }
    @PostMapping("/refresh") public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authentication.refresh(request.refreshToken()));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authentication.logout(request.refreshToken()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/sessions") @PreAuthorize("hasAuthority('PERM_SESSION_VIEW')")
    public ResponseEntity<List<SessionResponse>> sessions(Authentication principal) {
        return ResponseEntity.ok(authentication.listSessions(principal.getName()));
    }
    @DeleteMapping("/sessions/{id}") @PreAuthorize("hasAuthority('PERM_SESSION_REVOKE')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication principal) {
        authentication.revokeSession(principal.getName(), id); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/sessions") @PreAuthorize("hasAuthority('PERM_SESSION_REVOKE')")
    public ResponseEntity<Void> revokeAll(Authentication principal) {
        authentication.revokeAllSessions(principal.getName()); return ResponseEntity.noContent().build();
    }
}
