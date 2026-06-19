package com.example.switching.connector.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.switching.connector.entity.ConnectorConfigEntity;

public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfigEntity, Long> {

    Optional<ConnectorConfigEntity> findByConnectorName(String connectorName);

    Optional<ConnectorConfigEntity> findByConnectorNameAndEnabledTrue(String connectorName);

    List<ConnectorConfigEntity> findByBankCodeAndEnabledTrueOrderByConnectorNameAsc(String bankCode);

    List<ConnectorConfigEntity> findAllByOrderByConnectorNameAsc();
}