package com.example.switching.usermgmt.dto;

import java.time.Instant;
import java.util.Set;
import com.example.switching.usermgmt.enums.RoleType;
import com.example.switching.usermgmt.enums.UserStatus;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserStatus status,
        boolean mfaEnabled,
        Set<RoleType> roles,
        Instant lastLoginAt,
        Instant createdAt,
        String mfaEnrollmentSecret) {}
