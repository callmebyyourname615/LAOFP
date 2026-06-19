package com.example.switching.participant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.example.switching.participant.dto.CreateParticipantRequest;
import com.example.switching.participant.dto.ParticipantResponse;
import com.example.switching.participant.dto.UpdateParticipantRequest;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.enums.ParticipantType;
import com.example.switching.participant.exception.ParticipantAlreadyExistsException;
import com.example.switching.participant.exception.ParticipantNotFoundException;
import com.example.switching.participant.repository.ParticipantRepository;

@ExtendWith(MockitoExtension.class)
class ParticipantManagementServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    private ParticipantManagementService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantManagementService(participantRepository);
    }

    @Test
    void createNormalizesFieldsAndAppliesDefaults() {
        CreateParticipantRequest request = new CreateParticipantRequest();
        request.setBankCode(" bank_a ");
        request.setBankName(" Bank A ");
        request.setCountry(" la ");
        request.setCurrency(" lak ");

        when(participantRepository.findByBankCode("BANK_A"))
                .thenReturn(Optional.empty());

        when(participantRepository.save(any(ParticipantEntity.class)))
                .thenAnswer(invocation -> {
                    ParticipantEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        ParticipantResponse response = service.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("BANK_A", response.getBankCode());
        assertEquals("Bank A", response.getBankName());
        assertEquals("INACTIVE", response.getStatus());
        assertEquals("DIRECT", response.getParticipantType());
        assertEquals("LA", response.getCountry());
        assertEquals("LAK", response.getCurrency());

        verify(participantRepository).findByBankCode("BANK_A");
        verify(participantRepository).save(any(ParticipantEntity.class));
    }

    @Test
    void createRejectsDirectActiveStatus() {
        CreateParticipantRequest request = new CreateParticipantRequest();
        request.setBankCode("BANK_ACTIVE");
        request.setBankName("Active Bank");
        request.setCountry("LA");
        request.setCurrency("LAK");
        request.setStatus("ACTIVE");
        when(participantRepository.findByBankCode("BANK_ACTIVE")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.create(request));
        verify(participantRepository, never()).save(any(ParticipantEntity.class));
    }

    @Test
    void updateRejectsDirectStatusChange() {
        ParticipantEntity existing = new ParticipantEntity();
        existing.setId(1L);
        existing.setBankCode("BANK_A");
        existing.setBankName("Bank A");
        existing.setStatus(ParticipantStatus.INACTIVE);
        existing.setParticipantType(ParticipantType.DIRECT);
        existing.setCountry("LA");
        existing.setCurrency("LAK");
        UpdateParticipantRequest request = new UpdateParticipantRequest();
        request.setStatus("ACTIVE");
        when(participantRepository.findByBankCode("BANK_A")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.update("BANK_A", request));
        verify(participantRepository, never()).save(any(ParticipantEntity.class));
    }

    @Test
    void createThrowsWhenParticipantAlreadyExists() {
        CreateParticipantRequest request = new CreateParticipantRequest();
        request.setBankCode(" bank_a ");
        request.setBankName(" Bank A ");
        request.setCountry(" la ");
        request.setCurrency(" lak ");

        ParticipantEntity existing = new ParticipantEntity();
        existing.setId(1L);
        existing.setBankCode("BANK_A");

        when(participantRepository.findByBankCode("BANK_A"))
                .thenReturn(Optional.of(existing));

        assertThrows(ParticipantAlreadyExistsException.class, () -> service.create(request));

        verify(participantRepository).findByBankCode("BANK_A");
        verify(participantRepository, never()).save(any(ParticipantEntity.class));
    }

    @Test
    void updateChangesOnlyProvidedFieldsAndNormalizesValues() {
        ParticipantEntity existing = new ParticipantEntity();
        existing.setId(1L);
        existing.setBankCode("BANK_A");
        existing.setBankName("Bank A");
        existing.setStatus(ParticipantStatus.ACTIVE);
        existing.setParticipantType(ParticipantType.DIRECT);
        existing.setCountry("LA");
        existing.setCurrency("LAK");

        UpdateParticipantRequest request = new UpdateParticipantRequest();
        request.setBankName(" Bank A Updated ");
        request.setStatus(" active "); // same status is an allowed no-op; changes use the controlled workflow
        request.setCountry(" th ");
        request.setCurrency(" thb ");

        when(participantRepository.findByBankCode("BANK_A"))
                .thenReturn(Optional.of(existing));

        when(participantRepository.save(any(ParticipantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantResponse response = service.update(" bank_a ", request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("BANK_A", response.getBankCode());
        assertEquals("Bank A Updated", response.getBankName());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("DIRECT", response.getParticipantType());
        assertEquals("TH", response.getCountry());
        assertEquals("THB", response.getCurrency());

        verify(participantRepository).findByBankCode("BANK_A");
        verify(participantRepository).save(existing);
    }

    @Test
    void updateThrowsWhenParticipantNotFound() {
        UpdateParticipantRequest request = new UpdateParticipantRequest();
        request.setBankName(" Bank A Updated ");

        when(participantRepository.findByBankCode("BANK_A"))
                .thenReturn(Optional.empty());

        assertThrows(ParticipantNotFoundException.class, () -> service.update(" bank_a ", request));

        verify(participantRepository).findByBankCode("BANK_A");
        verify(participantRepository, never()).save(any(ParticipantEntity.class));
    }
}
