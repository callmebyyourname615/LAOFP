package com.example.switching.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import io.micrometer.tracing.Tracer;

class TraceContextSupportTest {
    @AfterEach void clear() { MDC.clear(); }

    @Test
    void acceptsOnlyW3cTraceIdFromMdcFallback() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        ObjectProvider<Tracer> provider = factory.getBeanProvider(Tracer.class);
        TraceContextSupport support = new TraceContextSupport(provider);
        MDC.put("traceId", "0123456789abcdef0123456789ABCDEF");
        assertThat(support.currentTraceId()).contains("0123456789abcdef0123456789abcdef");
        MDC.put("traceId", "account-123");
        assertThat(support.currentTraceId()).isEmpty();
    }
}
