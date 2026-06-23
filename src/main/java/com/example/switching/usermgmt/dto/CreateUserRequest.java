package com.example.switching.usermgmt.dto;

import java.util.Set;
import com.example.switching.usermgmt.enums.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(min = 12, max = 128) String initialPassword,
        @NotEmpty Set<RoleType> roles,
        boolean mfaEnabled,
        @Positive Long participantId) {}
