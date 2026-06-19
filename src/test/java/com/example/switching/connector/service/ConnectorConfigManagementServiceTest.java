package com.example.switching.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.switching.connector.dto.ConnectorConfigResponse;
import com.example.switching.connector.dto.CreateConnectorConfigRequest;
import com.example.switching.connector.dto.UpdateConnectorConfigRequest;
import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.enums.ConnectorType;
import com.example.switching.connector.exception.ConnectorConfigAlreadyExistsException;
import com.example.switching.connector.exception.ConnectorConfigNotFoundException;
import com.example.switching.connector.repository.ConnectorConfigRepository;
import com.example.switching.participant.service.ParticipantService;

@ExtendWith(MockitoExtension.class)
class ConnectorConfigManagementServiceTest {

    @Mock
    private ConnectorConfigRepository connectorConfigRepository;

    @Mock
    private ParticipantService participantService;

    private ConnectorConfigManagementService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorConfigManagementService(
                connectorConfigRepository,
                participantService
        );
    }

    @Test
    void createValidatesBankNormalizesFieldsAndAppliesDefaults() {
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorName(" bank_a_mock ");
        request.setBankCode(" bank_a ");
        request.setConnectorType(" mock ");
        request.setEndpointUrl(" http://localhost:8081/mock-bank-a ");

        when(connectorConfigRepository.findByConnectorName("BANK_A_MOCK"))
                .thenReturn(Optional.empty());

        when(connectorConfigRepository.save(any(ConnectorConfigEntity.class)))
                .thenAnswer(invocation -> {
                    ConnectorConfigEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        ConnectorConfigResponse response = service.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("BANK_A_MOCK", response.getConnectorName());
        assertEquals("BANK_A", response.getBankCode());
        assertEquals("MOCK", response.getConnectorType());
        assertEquals("http://localhost:8081/mock-bank-a", response.getEndpointUrl());
        assertEquals(5000, response.getTimeoutMs());
        assertEquals(Boolean.TRUE, response.getEnabled());
        assertEquals(Boolean.FALSE, response.getForceReject());

        verify(connectorConfigRepository).findByConnectorName("BANK_A_MOCK");
        verify(participantService).findByBankCode("BANK_A");
        verify(connectorConfigRepository).save(any(ConnectorConfigEntity.class));
    }

    @Test
    void createThrowsWhenConnectorNameAlreadyExists() {
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorName(" bank_a_mock ");
        request.setBankCode(" bank_a ");
        request.setConnectorType(" mock ");

        ConnectorConfigEntity existing = new ConnectorConfigEntity();
        existing.setId(1L);
        existing.setConnectorName("BANK_A_MOCK");

        when(connectorConfigRepository.findByConnectorName("BANK_A_MOCK"))
                .thenReturn(Optional.of(existing));

        assertThrows(ConnectorConfigAlreadyExistsException.class, () -> service.create(request));

        verify(connectorConfigRepository).findByConnectorName("BANK_A_MOCK");
        verify(connectorConfigRepository, never()).save(any(ConnectorConfigEntity.class));
    }

    @Test
    void createRejectsInvalidTimeout() {
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorName(" bank_a_mock ");
        request.setBankCode(" bank_a ");
        request.setConnectorType(" mock ");
        request.setTimeoutMs(0);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verify(connectorConfigRepository, never()).save(any(ConnectorConfigEntity.class));
    }

    @Test
    void updateChangesOnlyProvidedFieldsAndTrimsBlankableValues() {
        ConnectorConfigEntity existing = new ConnectorConfigEntity();
        existing.setId(1L);
        existing.setConnectorName("BANK_A_MOCK");
        existing.setBankCode("BANK_A");
        existing.setConnectorType(ConnectorType.MOCK);
        existing.setEndpointUrl("http://old-endpoint");
        existing.setTimeoutMs(5000);
        existing.setEnabled(true);
        existing.setForceReject(false);
        existing.setRejectReasonCode("OLD");
        existing.setRejectReasonMessage("Old message");

        UpdateConnectorConfigRequest request = new UpdateConnectorConfigRequest();
        request.setEndpointUrl("   ");
        request.setTimeoutMs(10000);
        request.setEnabled(false);
        request.setForceReject(true);
        request.setRejectReasonCode(" ac01 ");
        request.setRejectReasonMessage(" Account invalid ");

        when(connectorConfigRepository.findByConnectorName("BANK_A_MOCK"))
                .thenReturn(Optional.of(existing));

        when(connectorConfigRepository.save(any(ConnectorConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorConfigResponse response = service.update(" bank_a_mock ", request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("BANK_A_MOCK", response.getConnectorName());
        assertEquals("BANK_A", response.getBankCode());
        assertEquals("MOCK", response.getConnectorType());
        assertNull(response.getEndpointUrl());
        assertEquals(10000, response.getTimeoutMs());
        assertEquals(Boolean.FALSE, response.getEnabled());
        assertEquals(Boolean.TRUE, response.getForceReject());
        assertEquals("ac01", response.getRejectReasonCode());
        assertEquals("Account invalid", response.getRejectReasonMessage());

        assertFalse(existing.getEnabled());

        verify(connectorConfigRepository).findByConnectorName("BANK_A_MOCK");
        verify(connectorConfigRepository).save(existing);
    }

    @Test
    void updateThrowsWhenConnectorConfigNotFound() {
        UpdateConnectorConfigRequest request = new UpdateConnectorConfigRequest();
        request.setTimeoutMs(10000);

        when(connectorConfigRepository.findByConnectorName("BANK_A_MOCK"))
                .thenReturn(Optional.empty());

        assertThrows(ConnectorConfigNotFoundException.class, () -> service.update(" bank_a_mock ", request));

        verify(connectorConfigRepository).findByConnectorName("BANK_A_MOCK");
        verify(connectorConfigRepository, never()).save(any(ConnectorConfigEntity.class));
    }
}
