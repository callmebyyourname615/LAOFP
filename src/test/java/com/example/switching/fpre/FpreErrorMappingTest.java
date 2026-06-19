package com.example.switching.fpre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.switching.common.error.ErrorCatalog;

class FpreErrorMappingTest {

    @Test
    void fpreErrorCatalogMapsRequiredCodes() {
        assertEquals("LFP-FPRE-001", ErrorCatalog.LFP_FPRE_001.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ErrorCatalog.LFP_FPRE_001.getHttpStatus());
        assertFalse(ErrorCatalog.LFP_FPRE_001.isRetryable());

        assertEquals("LFP-FPRE-002", ErrorCatalog.LFP_FPRE_002.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCatalog.LFP_FPRE_002.getHttpStatus());
        assertFalse(ErrorCatalog.LFP_FPRE_002.isRetryable());

        assertEquals("LFP-FPRE-003", ErrorCatalog.LFP_FPRE_003.getErrorCode());
        assertEquals(HttpStatus.ACCEPTED, ErrorCatalog.LFP_FPRE_003.getHttpStatus());
        assertTrue(ErrorCatalog.LFP_FPRE_003.isRetryable());
    }
}
