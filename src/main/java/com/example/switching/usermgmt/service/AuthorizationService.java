package com.example.switching.usermgmt.service;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.usermgmt.entity.RoleEntity;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.repository.UserRepository;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class AuthorizationService {
    private final UserRepository users;
    public AuthorizationService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserEntity requireUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("SMOS user not found"));
    }

    public Set<String> roles(UserEntity user) {
        Set<String> values = new LinkedHashSet<>();
        for (RoleEntity role : user.getRoles()) values.add(role.getName().name());
        return Set.copyOf(values);
    }

    public Set<String> permissions(UserEntity user) {
        Set<String> values = new LinkedHashSet<>();
        for (RoleEntity role : user.getRoles()) {
            role.getPermissions().forEach(permission -> values.add(permission.authority()));
        }
        return Set.copyOf(values);
    }

    public boolean hasPermission(UserEntity user, String permission) {
        return permissions(user).contains(permission);
    }

    public void requirePermission(UserEntity user, String permission) {
        if (!hasPermission(user, permission)) {
            throw new SecurityException("Missing permission: " + permission);
        }
    }
}
