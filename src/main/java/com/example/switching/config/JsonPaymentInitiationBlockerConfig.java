package com.example.switching.config;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class JsonPaymentInitiationBlockerConfig implements WebMvcConfigurer {

    @Value("${switching.payment.json-initiation.enabled:true}")
    private boolean jsonPaymentInitiationEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JsonPaymentInitiationBlocker());
    }

    private class JsonPaymentInitiationBlocker implements HandlerInterceptor {

        @Override
        public boolean preHandle(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler
        ) throws Exception {
            if (jsonPaymentInitiationEnabled) {
                return true;
            }

            if (!HttpMethod.POST.matches(request.getMethod())) {
                return true;
            }

            String path = request.getRequestURI();

            if (!isJsonPaymentInitiationPath(path)) {
                return true;
            }

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            response.getWriter().write("""
                    {
                      "error": "JSON_PAYMENT_INITIATION_DISABLED",
                      "errorCode": "ISO-ONLY-001",
                      "message": "JSON payment initiation is disabled. Member banks must send ISO 20022 XML using POST /api/iso20022/pacs008.",
                      "status": 403,
                      "path": "%s",
                      "timestamp": "%s"
                    }
                    """.formatted(escapeJson(path), LocalDateTime.now()));

            return false;
        }

        private boolean isJsonPaymentInitiationPath(String path) {
            return "/api/inquiries".equals(path)
                    || "/api/transfers".equals(path);
        }

        private String escapeJson(String value) {
            if (value == null) {
                return "";
            }

            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
}