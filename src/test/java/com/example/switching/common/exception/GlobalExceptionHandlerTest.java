package com.example.switching.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

import com.example.switching.common.dto.ApiErrorResponse;
import com.example.switching.observability.tracing.TraceContextSupport;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private TraceContextSupport traceContext;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(traceContext.currentTraceId()).thenReturn(Optional.empty());
        handler = new GlobalExceptionHandler(traceContext);
    }

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/reports/download/test");

        ApiErrorResponse response = handler.handleMissingRequestParameter(
                new MissingServletRequestParameterException("expires", "long"), request).getBody();

        assertEquals(400, response.getStatus());
        assertEquals("REQ-001", response.getErrorCode());
        assertEquals("Required query parameter is missing: expires", response.getMessage());
        assertEquals("expires", response.getDetails().get("parameter"));
    }

    @Test
    void securityExceptionReturnsForbiddenWithoutLeakingDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/reports/download/test");

        ApiErrorResponse response = handler.handleSecurityException(
                new SecurityException("Invalid download token: secret-value"), request).getBody();

        assertEquals(403, response.getStatus());
        assertEquals("LFP-2004", response.getErrorCode());
        assertEquals("Access denied", response.getMessage());
    }
}
