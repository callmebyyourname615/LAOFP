package com.example.switching.usermgmt.controller;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.switching.usermgmt.service.SmosAuthenticationException;

@RestControllerAdvice(basePackages = "com.example.switching.usermgmt")
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SmosExceptionHandler {
    @ExceptionHandler(SmosAuthenticationException.class)
    ResponseEntity<Map<String, Object>> authentication(SmosAuthenticationException ex) {
        return response(HttpStatus.UNAUTHORIZED, "SMOS-401", ex.getMessage());
    }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String, Object>> forbidden(SecurityException ex) {
        return response(HttpStatus.FORBIDDEN, "SMOS-403", ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        return response(HttpStatus.CONFLICT, "SMOS-409", ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, "SMOS-400", ex.getMessage());
    }
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "errorCode", code,
                "message", message == null ? status.getReasonPhrase() : message));
    }
}
