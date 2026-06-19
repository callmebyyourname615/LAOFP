package com.example.switching.common.error;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class ErrorClassifier {

    public ErrorCatalog classify(Throwable throwable) {
        if (throwable == null) {
            return ErrorCatalog.SYS_001;
        }

        if (hasCause(throwable, SocketTimeoutException.class)) {
            return ErrorCatalog.NET_002;
        }

        if (hasCause(throwable, ConnectException.class)) {
            return ErrorCatalog.NET_001;
        }

        if (hasCause(throwable, UnknownHostException.class)) {
            return ErrorCatalog.NET_003;
        }

        if (hasCause(throwable, SSLException.class)) {
            return ErrorCatalog.NET_004;
        }

        if (hasCause(throwable, JsonProcessingException.class)) {
            return ErrorCatalog.OUT_001;
        }

        if (hasCause(throwable, DataIntegrityViolationException.class)) {
            return ErrorCatalog.INF_DB_002;
        }

        if (hasCause(throwable, DataAccessException.class)) {
            return ErrorCatalog.INF_DB_001;
        }

        if (throwable instanceof IllegalStateException) {
            return ErrorCatalog.OUT_002;
        }

        return ErrorCatalog.SYS_001;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> targetClass) {
        Throwable current = throwable;

        while (current != null) {
            if (targetClass.isAssignableFrom(current.getClass())) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }
}