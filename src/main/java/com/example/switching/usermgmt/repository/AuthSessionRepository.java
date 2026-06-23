package com.example.switching.usermgmt.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.switching.usermgmt.entity.AuthSessionEntity;
import com.example.switching.usermgmt.enums.AuthSessionType;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
    @Query("select s from AuthSessionEntity s join fetch s.user u where s.tokenHash=:hash and s.sessionType=:type and s.revokedAt is null and s.expiresAt>:now")
    Optional<AuthSessionEntity> findActive(@Param("hash") String hash, @Param("type") AuthSessionType type, @Param("now") Instant now);

    @Query("select s from AuthSessionEntity s join fetch s.user u where s.tokenHash=:hash and s.sessionType=:type")
    Optional<AuthSessionEntity> findByTokenHashAndType(@Param("hash") String hash, @Param("type") AuthSessionType type);

    @Query("select s from AuthSessionEntity s where s.user.id=:userId and s.sessionType=:type and s.revokedAt is null and s.expiresAt>:now order by s.createdAt desc")
    List<AuthSessionEntity> findActiveByUser(@Param("userId") Long userId, @Param("type") AuthSessionType type, @Param("now") Instant now);

    @Modifying
    @Query("update AuthSessionEntity s set s.revokedAt=:now where s.user.id=:userId and s.sessionType=:type and s.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("type") AuthSessionType type, @Param("now") Instant now);

    @Modifying
    @Query("update AuthSessionEntity s set s.revokedAt=:now where s.sessionFamilyId=:familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
