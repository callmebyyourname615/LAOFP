package com.example.switching.usermgmt.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.switching.usermgmt.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Override
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Page<UserEntity> findAll(Pageable pageable);
    @Override
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findById(Long id);
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
}
