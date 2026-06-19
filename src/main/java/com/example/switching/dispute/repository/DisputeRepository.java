package com.example.switching.dispute.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.dispute.entity.DisputeEntity;

public interface DisputeRepository extends JpaRepository<DisputeEntity, Long> {

    List<DisputeEntity> findByRaisingPspIdOrRespondingPspIdOrderByRaisedAtDesc(
            String raisingPspId, String respondingPspId);
}
