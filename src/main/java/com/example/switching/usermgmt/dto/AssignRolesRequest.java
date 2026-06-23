package com.example.switching.usermgmt.dto;

import java.util.Set;
import com.example.switching.usermgmt.enums.RoleType;
import jakarta.validation.constraints.NotEmpty;

public record AssignRolesRequest(@NotEmpty Set<RoleType> roles) {}
