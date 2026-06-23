package com.example.switching.usermgmt.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import com.example.switching.usermgmt.entity.MakerCheckerRequestEntity;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;
import jakarta.persistence.LockModeType;

public interface MakerCheckerRequestRepository extends JpaRepository<MakerCheckerRequestEntity, UUID> {
    List<MakerCheckerRequestEntity> findTop100ByStatusOrderBySubmittedAtAsc(MakerCheckerStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MakerCheckerRequestEntity> findWithLockById(UUID id);
}
