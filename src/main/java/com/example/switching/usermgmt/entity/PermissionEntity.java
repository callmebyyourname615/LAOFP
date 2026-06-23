package com.example.switching.usermgmt.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "smos_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action"}))
public class PermissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64) private String resource;
    @Column(nullable = false, length = 32) private String action;
    @Column(nullable = false, length = 256) private String description;
    public Long getId() { return id; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public String authority() { return resource + "." + action; }
}
