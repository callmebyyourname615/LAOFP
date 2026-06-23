package com.example.switching.observability.tracing;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/** Safe access to the current W3C trace identifier. */
@Component
public class TraceContextSupport {
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private final ObjectProvider<Tracer> tracer;

    public TraceContextSupport(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public Optional<String> currentTraceId() {
        Tracer activeTracer = tracer.getIfAvailable();
        if (activeTracer != null) {
            Span span = activeTracer.currentSpan();
            if (span != null) {
                String traceId = normalize(span.context().traceId());
                if (traceId != null) return Optional.of(traceId);
            }
        }
        String mdc = normalize(MDC.get("traceId"));
        return Optional.ofNullable(mdc);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return TRACE_ID.matcher(normalized).matches() ? normalized : null;
    }
}
