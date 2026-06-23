package com.example.switching.usermgmt.controller;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.switching.usermgmt.dto.*;
import com.example.switching.usermgmt.service.UserManagementService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class UserController {
    private final UserManagementService users;
    public UserController(UserManagementService users) { this.users = users; }

    @GetMapping public ResponseEntity<List<UserResponse>> list() { return ResponseEntity.ok(users.list()); }
    @GetMapping("/{id}") public ResponseEntity<UserResponse> get(@PathVariable Long id) { return ResponseEntity.ok(users.get(id)); }
    @PostMapping public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.create(request, authentication.getName()));
    }
    @PutMapping("/{id}/roles") public ResponseEntity<UserResponse> assignRoles(@PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request, Authentication authentication) {
        return ResponseEntity.ok(users.assignRoles(id, request.roles(), authentication.getName()));
    }
    @PutMapping("/{id}/status") public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request, Authentication authentication) {
        return ResponseEntity.ok(users.updateStatus(id, request.status(), authentication.getName()));
    }
}
