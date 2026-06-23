package com.example.switching.usermgmt.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import com.example.switching.usermgmt.enums.RoleType;
import jakarta.persistence.*;

@Entity
@Table(name = "smos_roles")
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true, length = 40)
    private RoleType name;
    @Column(nullable = false, length = 256) private String description;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "smos_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions = new LinkedHashSet<>();
    public Long getId() { return id; }
    public RoleType getName() { return name; }
    public String getDescription() { return description; }
    public Set<PermissionEntity> getPermissions() { return permissions; }
}
