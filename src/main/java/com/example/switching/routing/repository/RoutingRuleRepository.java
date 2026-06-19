package com.example.switching.routing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.routing.entity.RoutingRuleEntity;

public interface RoutingRuleRepository extends JpaRepository<RoutingRuleEntity, Long> {

    @Query("""
            SELECT r
            FROM RoutingRuleEntity r
            ORDER BY r.sourceBank ASC,
                     r.destinationBank ASC,
                     r.messageType ASC,
                     r.priority ASC,
                     r.routeCode ASC
            """)
    List<RoutingRuleEntity> findAllOrdered();

    Optional<RoutingRuleEntity> findByRouteCode(String routeCode);

    Optional<RoutingRuleEntity> findFirstBySourceBankAndDestinationBankAndMessageTypeAndEnabledTrueOrderByPriorityAsc(
            String sourceBank,
            String destinationBank,
            IsoMessageType messageType);
}