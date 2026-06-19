package com.example.switching.routing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.example.switching.connector.service.ConnectorConfigService;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.participant.service.ParticipantService;
import com.example.switching.routing.dto.CreateRoutingRuleRequest;
import com.example.switching.routing.dto.RoutingRuleResponse;
import com.example.switching.routing.dto.UpdateRoutingRuleRequest;
import com.example.switching.routing.entity.RoutingRuleEntity;
import com.example.switching.routing.exception.RoutingRuleAlreadyExistsException;
import com.example.switching.routing.exception.RoutingRuleNotFoundException;
import com.example.switching.routing.repository.RoutingRuleRepository;

@ExtendWith(MockitoExtension.class)
class RoutingRuleManagementServiceTest {

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @Mock
    private ParticipantService participantService;

    @Mock
    private ConnectorConfigService connectorConfigService;

    @Mock
    private RoutingService routingService;

    private RoutingRuleManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoutingRuleManagementService(
                routingRuleRepository,
                participantService,
                connectorConfigService,
                routingService
        );
    }

    @Test
    void createValidatesParticipantsNormalizesFieldsAndClearsCache() {
        CreateRoutingRuleRequest request = new CreateRoutingRuleRequest();
        request.setRouteCode(" route_ab ");
        request.setSourceBank(" bank_a ");
        request.setDestinationBank(" bank_b ");
        request.setMessageType(" pacs_008 ");
        request.setConnectorName(" bank_b_mock ");

        when(routingRuleRepository.findByRouteCode("ROUTE_AB"))
                .thenReturn(Optional.empty());

        when(routingRuleRepository.save(any(RoutingRuleEntity.class)))
                .thenAnswer(invocation -> {
                    RoutingRuleEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        RoutingRuleResponse response = service.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("ROUTE_AB", response.getRouteCode());
        assertEquals("BANK_A", response.getSourceBank());
        assertEquals("BANK_B", response.getDestinationBank());
        assertEquals("PACS_008", response.getMessageType());
        assertEquals("BANK_B_MOCK", response.getConnectorName());
        assertEquals(1, response.getPriority());
        assertEquals(Boolean.TRUE, response.getEnabled());

        verify(routingRuleRepository).findByRouteCode("ROUTE_AB");
        verify(participantService).findByBankCode("BANK_A");
        verify(participantService).findByBankCode("BANK_B");
        verify(connectorConfigService).requireByConnectorName("BANK_B_MOCK");
        verify(routingRuleRepository).save(any(RoutingRuleEntity.class));
        verify(routingService).clearCache();
    }

    @Test
    void createThrowsWhenRouteCodeAlreadyExists() {
        CreateRoutingRuleRequest request = new CreateRoutingRuleRequest();
        request.setRouteCode(" route_ab ");
        request.setSourceBank(" bank_a ");
        request.setDestinationBank(" bank_b ");
        request.setMessageType(" pacs_008 ");
        request.setConnectorName(" bank_b_mock ");

        RoutingRuleEntity existing = new RoutingRuleEntity();
        existing.setId(1L);
        existing.setRouteCode("ROUTE_AB");

        when(routingRuleRepository.findByRouteCode("ROUTE_AB"))
                .thenReturn(Optional.of(existing));

        assertThrows(RoutingRuleAlreadyExistsException.class, () -> service.create(request));

        verify(routingRuleRepository).findByRouteCode("ROUTE_AB");
        verify(routingRuleRepository, never()).save(any(RoutingRuleEntity.class));
    }

    @Test
    void createRejectsInvalidPriority() {
        CreateRoutingRuleRequest request = new CreateRoutingRuleRequest();
        request.setRouteCode(" route_ab ");
        request.setSourceBank(" bank_a ");
        request.setDestinationBank(" bank_b ");
        request.setMessageType(" pacs_008 ");
        request.setConnectorName(" bank_b_mock ");
        request.setPriority(0);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verify(routingRuleRepository, never()).save(any(RoutingRuleEntity.class));
    }

    @Test
    void updateChangesOptionalFieldsAndClearsCache() {
        RoutingRuleEntity existing = new RoutingRuleEntity();
        existing.setId(1L);
        existing.setRouteCode("ROUTE_AB");
        existing.setSourceBank("BANK_A");
        existing.setDestinationBank("BANK_B");
        existing.setMessageType(IsoMessageType.PACS_008);
        existing.setConnectorName("BANK_B_MOCK");
        existing.setPriority(1);
        existing.setEnabled(true);

        UpdateRoutingRuleRequest request = new UpdateRoutingRuleRequest();
        request.setConnectorName(" bank_c_mock ");
        request.setPriority(5);
        request.setEnabled(false);

        when(routingRuleRepository.findByRouteCode("ROUTE_AB"))
                .thenReturn(Optional.of(existing));

        when(routingRuleRepository.save(any(RoutingRuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRuleResponse response = service.update(" route_ab ", request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("ROUTE_AB", response.getRouteCode());
        assertEquals("BANK_A", response.getSourceBank());
        assertEquals("BANK_B", response.getDestinationBank());
        assertEquals("PACS_008", response.getMessageType());
        assertEquals("BANK_C_MOCK", response.getConnectorName());
        assertEquals(5, response.getPriority());
        assertEquals(Boolean.FALSE, response.getEnabled());

        assertEquals("BANK_C_MOCK", existing.getConnectorName());
        assertEquals(5, existing.getPriority());
        assertFalse(existing.getEnabled());

        verify(routingRuleRepository).findByRouteCode("ROUTE_AB");
        verify(connectorConfigService).requireByConnectorName("BANK_C_MOCK");
        verify(routingRuleRepository).save(existing);
        verify(routingService).clearCache();
    }

    @Test
    void updateThrowsWhenRoutingRuleNotFound() {
        UpdateRoutingRuleRequest request = new UpdateRoutingRuleRequest();
        request.setPriority(5);

        when(routingRuleRepository.findByRouteCode("ROUTE_AB"))
                .thenReturn(Optional.empty());

        assertThrows(RoutingRuleNotFoundException.class, () -> service.update(" route_ab ", request));

        verify(routingRuleRepository).findByRouteCode("ROUTE_AB");
        verify(routingRuleRepository, never()).save(any(RoutingRuleEntity.class));
    }
}
