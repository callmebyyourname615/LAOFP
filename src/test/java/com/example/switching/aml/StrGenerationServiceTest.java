package com.example.switching.aml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.aml.config.AmlProperties;
import com.example.switching.aml.service.StrGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class StrGenerationServiceTest {

    @Mock JdbcTemplate jdbcTemplate;

    private AmlProperties properties;
    private StrGenerationService service;

    @BeforeEach
    void setUp() {
        properties = new AmlProperties();
        properties.setBolFiuUrl("http://127.0.0.1:1");
        properties.setBolFiuApiKey("test-key");
        service = new StrGenerationService(jdbcTemplate, properties, new ObjectMapper());
    }

    @Test
    void generateStrQuietly_insertsPendingSubmissionPayload() {
        service.generateStrQuietly("TXN-1", "MATCHED NAME", "OFAC");

        verify(jdbcTemplate).update(
                any(String.class),
                eq("TXN-1"),
                any(String.class),
                eq("MATCHED NAME"),
                eq("OFAC"));
    }

    @Test
    void submitSingleStr_transportError_marksFailureAndChecksDeadLetterState() throws Exception {
        when(jdbcTemplate.update(any(String.class), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(7L))).thenReturn(1);

        Method method = StrGenerationService.class.getDeclaredMethod(
                "submitSingleStr", Long.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, 7L, "TXN-7", "{}");

        verify(jdbcTemplate).update(any(String.class), any(), eq(7L));
    }
}
