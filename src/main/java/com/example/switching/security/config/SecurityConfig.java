package com.example.switching.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.switching.security.filter.ApiKeyAuthFilter;
import com.example.switching.security.breakglass.filter.BreakGlassFilter;
import com.example.switching.security.mtls.MtlsCertificateValidator;
import com.example.switching.security.mtls.MtlsFilter;
import com.example.switching.security.mtls.TrustedProxyHeaderFilter;
import com.example.switching.security.oauth.OAuthTokenFilter;
import com.example.switching.security.oauth.service.OAuthTokenService;
import com.example.switching.security.repository.ApiKeyRepository;
import com.example.switching.security.signing.HmacSignatureVerifier;
import com.example.switching.security.signing.RequestSignatureFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiKeyRepository apiKeyRepository;
    private final HmacSignatureVerifier hmacSignatureVerifier;
    private final OAuthTokenService oAuthTokenService;
    private final MtlsCertificateValidator mtlsCertificateValidator;
    private final boolean apiKeyEnabled;
    private final boolean oauthEnabled;
    private final boolean mtlsEnabled;
    private final String mtlsCertHeader;
    private final boolean signingEnabled;
    private final String apiV1Prefix;
    private final BreakGlassFilter breakGlassFilter;

    public SecurityConfig(
            ApiKeyRepository apiKeyRepository,
            HmacSignatureVerifier hmacSignatureVerifier,
            OAuthTokenService oAuthTokenService,
            MtlsCertificateValidator mtlsCertificateValidator,
            @Value("${switching.security.api-key.enabled:true}") boolean apiKeyEnabled,
            @Value("${switching.security.oauth.enabled:false}") boolean oauthEnabled,
            @Value("${switching.security.mtls.enabled:false}") boolean mtlsEnabled,
            @Value("${switching.security.mtls.cert-header:ssl-client-cert}") String mtlsCertHeader,
            @Value("${switching.security.signing.enabled:false}") boolean signingEnabled,
            @Value("${switching.api.v1-prefix}") String apiV1Prefix,
            BreakGlassFilter breakGlassFilter) {
        this.apiKeyRepository = apiKeyRepository;
        this.hmacSignatureVerifier = hmacSignatureVerifier;
        this.oAuthTokenService = oAuthTokenService;
        this.mtlsCertificateValidator = mtlsCertificateValidator;
        this.apiKeyEnabled = apiKeyEnabled;
        this.oauthEnabled = oauthEnabled;
        this.mtlsEnabled = mtlsEnabled;
        this.mtlsCertHeader = mtlsCertHeader;
        this.signingEnabled = signingEnabled;
        this.apiV1Prefix = normalizePrefix(apiV1Prefix);
        this.breakGlassFilter = breakGlassFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Disable CSRF (stateless REST API) and sessions
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!apiKeyEnabled) {
            // Test / dev profile — allow all requests without auth
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        // ── Auth filter chain ─────────────────────────────────────────────────
        //
        // Order (when both flags are on):
        //   OAuthTokenFilter → ApiKeyAuthFilter → RequestSignatureFilter
        //
        // OAuthTokenFilter activates only when Authorization: Bearer is present.
        // ApiKeyAuthFilter activates only when X-API-Key is present.
        // Both can coexist during the grace period while PSPs migrate to OAuth.

        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(apiKeyRepository);
        http.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (oauthEnabled) {
            // Run OAuth before ApiKey so Bearer tokens are authenticated first.
            http.addFilterBefore(new OAuthTokenFilter(oAuthTokenService), ApiKeyAuthFilter.class);
        }

        if (mtlsEnabled) {
            // Strip spoofed client-certificate headers before every authentication filter.
            TrustedProxyHeaderFilter trustedProxyHeaderFilter = new TrustedProxyHeaderFilter(mtlsCertHeader);
            if (oauthEnabled) {
                http.addFilterBefore(trustedProxyHeaderFilter, OAuthTokenFilter.class);
            } else {
                http.addFilterBefore(trustedProxyHeaderFilter, ApiKeyAuthFilter.class);
            }
            // mTLS runs after API-key/OAuth identity is available and before request signing.
            http.addFilterAfter(new MtlsFilter(mtlsCertificateValidator, mtlsCertHeader, apiV1Prefix), ApiKeyAuthFilter.class);
        }

        if (signingEnabled) {
            RequestSignatureFilter signatureFilter = new RequestSignatureFilter(hmacSignatureVerifier, apiV1Prefix);
            if (mtlsEnabled) {
                http.addFilterAfter(signatureFilter, MtlsFilter.class);
            } else {
                http.addFilterAfter(signatureFilter, ApiKeyAuthFilter.class);
            }
        }

        // Record break-glass use only after all ordinary authentication, mTLS, and HMAC checks pass.
        if (signingEnabled) {
            http.addFilterAfter(breakGlassFilter, RequestSignatureFilter.class);
        } else if (mtlsEnabled) {
            http.addFilterAfter(breakGlassFilter, MtlsFilter.class);
        } else {
            http.addFilterAfter(breakGlassFilter, ApiKeyAuthFilter.class);
        }

        http.authorizeHttpRequests(auth -> auth

                        // ── Public (no key required) ──────────────────────────────
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                // OAuth token endpoint — PSPs authenticate *here* with
                                // client_id + client_secret; Bearer token is returned
                                v1("/oauth/token"),
                                v1("/oauth/token/revoke"),
                                v1("/settlement/rtgs-callback")
                        ).permitAll()

                        // ── Ops/Internal actuator endpoints — never expose publicly ──
                        // /actuator/prometheus is served on management port 9090 in prod
                        // (see MANAGEMENT_PORT in configmap.yaml) so this rule is a
                        // defence-in-depth guard if both ports are accidentally proxied.
                        .requestMatchers(
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/actuator/env",
                                "/actuator/env/**",
                                "/actuator/beans",
                                "/actuator/configprops"
                        ).hasAnyRole("OPS", "ADMIN")

                        // ── VPA / Account Lookup (P11) ───────────────────────────
                        .requestMatchers(HttpMethod.POST,   v1("/lookup/resolve")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST,   v1("/lookup/vpa/register")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,    v1("/lookup/vpa/*")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, v1("/lookup/vpa/*")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,    v1("/lookup/vpa/*")).hasAnyRole("BANK", "OPS", "ADMIN")

                        // ── BANK role — webhook registration (P12) ───────────────
                        .requestMatchers(HttpMethod.POST,   v1("/webhooks")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,    v1("/webhooks")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,    v1("/webhooks/**")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, v1("/webhooks/**")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST,   v1("/webhooks/*/test")).hasAnyRole("BANK", "ADMIN")

                        // ── BANK role — payment path ──────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/inquiries/**").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/transfers").hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/transfers/**").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/transfers").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers("/api/iso20022/**").hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/iso-messages/**").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/iso-inquiries/**").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/transfers/*/retry-status")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/transfers/*/retry-history")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/transfers/pending")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/transfers/failed")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/fpre/health")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/settlement/balance")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/settlement/pool-history")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/settlement/liquidity/topup")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/settlement/positions")).hasAnyRole("OPS", "ADMIN")
                        // ── Settlement reports — camt.054 (P14) ─────────────────
                        .requestMatchers(HttpMethod.GET, "/api/operations/settlement/cycles/*/report").hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/operations/settlement/cycles/*/reports").hasAnyRole("OPS", "ADMIN")

                        // ── QR Code Service (P15) ─────────────────────────────
                        .requestMatchers(HttpMethod.POST, v1("/qr/generate/static")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/qr/generate/dynamic")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/qr/decode")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/qr/pay")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/qr/refund")).hasAnyRole("BANK", "ADMIN")

                        // ── Bill Payment Service (P16) ────────────────────────────
                        .requestMatchers(HttpMethod.GET,  v1("/billers")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/billers/*")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/bills/fetch")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/bills/pay")).hasAnyRole("BANK", "ADMIN")

                        // ── Dispute & Refund Manager (P18) ────────────────────────
                        .requestMatchers(HttpMethod.POST, v1("/disputes/raise")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/disputes")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/disputes/*")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,  v1("/disputes/*/respond")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/disputes/*/resolve")).hasAnyRole("BANK", "ADMIN")

                        // ── Cross-border Payment (P17) ────────────────────────────
                        .requestMatchers(HttpMethod.GET,  v1("/crossborder/corridors")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  v1("/crossborder/fx-rates")).hasAnyRole("BANK", "OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/crossborder/quote")).hasAnyRole("BANK", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/crossborder/initiate")).hasAnyRole("BANK", "ADMIN")

                        // ── ADMIN only — P9 credential management ────────────────
                        .requestMatchers(HttpMethod.POST,   v1("/participants/*/credentials/rotate")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,   v1("/participants/*/certificates/register")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, v1("/participants/*/certificates/*")).hasRole("ADMIN")

                        // ── ADMIN only — API key management ──────────────────────
                        .requestMatchers("/api/admin/api-keys/**").hasRole("ADMIN")

                        // ── ADMIN only — destructive operations actions ──────────
                        .requestMatchers(HttpMethod.POST, "/api/operations/outbox-failures/retry-all").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/operations/outbox-stuck/recover-all").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/operations/bank-onboarding").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/operations/bank-onboarding/generate-routes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/operations/connectors/*/test").hasRole("ADMIN")

                        // ── Dashboard (OPS / ADMIN) ──────────────────────────────
                        .requestMatchers("/api/dashboard/**").hasAnyRole("OPS", "ADMIN")

                        // ── OPS role — operations & monitoring ────────────────────
                        .requestMatchers("/api/operations/**").hasAnyRole("OPS", "ADMIN")
                        .requestMatchers("/api/outbox-events/**").hasAnyRole("OPS", "ADMIN")

                        // ── ADMIN only — configuration management ─────────────────
                        .requestMatchers(HttpMethod.POST,  "/api/participants").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/participants/**").denyAll()
                        .requestMatchers(HttpMethod.GET,   "/api/participants/**").hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.GET,   "/api/participants").hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST,  "/api/routing-rules/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/routing-rules/**").denyAll()
                        .requestMatchers(HttpMethod.GET,   "/api/routing-rules/**").hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST,  "/api/connector-configs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/connector-configs/**").denyAll()
                        .requestMatchers(HttpMethod.GET,   "/api/connector-configs/**").hasAnyRole("OPS", "ADMIN")

                        // ── Participant certification evidence ────────────────────
                        .requestMatchers(HttpMethod.GET,  v1("/operations/participant-certifications")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/participant-certifications")).hasRole("ADMIN")

                        // ── Four-eyes configuration changes ───────────────────────
                        .requestMatchers(HttpMethod.GET,  v1("/operations/config-changes")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/config-changes")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/config-changes/*/approve")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/config-changes/*/execute")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/config-changes/*/reject")).hasRole("ADMIN")

                        // ── Privileged access / break-glass lifecycle ─────────────
                        .requestMatchers(HttpMethod.GET,  v1("/operations/break-glass")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/break-glass")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/break-glass/*/approve")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/break-glass/*/revoke")).hasRole("ADMIN")

                        // ── Legal holds and retention controls ─────────────────────
                        .requestMatchers(HttpMethod.GET,  v1("/operations/legal-holds")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/legal-holds")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/legal-holds/*/approve")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/legal-holds/*/request-release")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/legal-holds/*/approve-release")).hasRole("ADMIN")

                        // ── Controlled dead-letter quarantine and replay ───────────
                        .requestMatchers(HttpMethod.GET,  v1("/operations/dead-letters")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/dead-letters/*/request-replay")).hasAnyRole("OPS", "ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/dead-letters/*/approve-replay")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/dead-letters/*/execute-replay")).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, v1("/operations/dead-letters/*/discard")).hasRole("ADMIN")

                        // ── Catch-all — must be authenticated ─────────────────────
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("""
                                    {"status":401,"error":"UNAUTHORIZED","errorCode":"SEC-001",\
                                    "message":"Missing or invalid X-API-Key header",\
                                    "path":"%s"}""".formatted(req.getRequestURI()));
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("""
                                    {"status":403,"error":"FORBIDDEN","errorCode":"SEC-002",\
                                    "message":"Insufficient role for this endpoint",\
                                    "path":"%s"}""".formatted(req.getRequestURI()));
                        })
                );

        return http.build();
    }

    private String v1(String path) {
        return apiV1Prefix + path;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/v1";
        }
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
