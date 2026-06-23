package com.example.switching.usermgmt.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.switching.usermgmt.entity.RoleEntity;
import com.example.switching.usermgmt.enums.RoleType;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findByNameIn(Collection<RoleType> names);
    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByName(RoleType name);
}
