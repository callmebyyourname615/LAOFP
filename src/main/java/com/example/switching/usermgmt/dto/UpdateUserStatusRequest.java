package com.example.switching.usermgmt.dto;

import com.example.switching.usermgmt.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {}
