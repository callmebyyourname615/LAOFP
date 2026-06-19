package com.example.switching.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.switching.AbstractIntegrationTest;

@TestPropertySource(properties = "switching.security.api-key.enabled=true")
class SecurityAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_KEY = "sk-admin-switching-2026";
    private static final String OPS_KEY = "sk-ops-switching-2026";
    private static final String BANK_KEY = "sk-bank-a-switching-2026";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void protectedEndpointWithoutApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/operations/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bankRoleCannotAccessOperationsEndpoint() throws Exception {
        mockMvc.perform(get("/api/operations/health")
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsRoleCanAccessReadOnlyOperationsEndpoint() throws Exception {
        mockMvc.perform(get("/api/operations/health")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void opsRoleCannotCallAdminOnlyOperationsActions() throws Exception {
        mockMvc.perform(post("/api/operations/outbox-failures/retry-all")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/operations/outbox-stuck/recover-all")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/operations/connectors/MOCK_BANK_B_CONNECTOR/test")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/operations/bank-onboarding")
                        .header("X-API-Key", OPS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bankOnboardingBody("BANK_AUTH_OPS")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .header("X-API-Key", OPS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "BANK_A",
                                  "mode": "BOTH"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleCanCallAdminOnlyOperationsActions() throws Exception {
        mockMvc.perform(post("/api/operations/outbox-failures/retry-all")
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/operations/outbox-stuck/recover-all")
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/operations/connectors/MOCK_BANK_B_CONNECTOR/test")
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/operations/bank-onboarding/generate-routes")
                        .header("X-API-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "BANK_A",
                                  "mode": "BOTH"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void opsRoleCannotModifyConfigurationEndpoints() throws Exception {
        mockMvc.perform(post("/api/participants")
                        .header("X-API-Key", OPS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "BANK_AUTH_CFG",
                                  "bankName": "Auth Config Test Bank",
                                  "participantType": "DIRECT",
                                  "country": "LA",
                                  "currency": "LAK",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/routing-rules/cache/clear")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/connector-configs/MOCK_BANK_B_CONNECTOR")
                        .header("X-API-Key", OPS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleCanAccessApiKeyManagement() throws Exception {
        mockMvc.perform(get("/api/admin/api-keys")
                        .header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void opsAndBankRolesCannotAccessApiKeyManagement() throws Exception {
        mockMvc.perform(get("/api/admin/api-keys")
                        .header("X-API-Key", OPS_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/api-keys")
                        .header("X-API-Key", BANK_KEY))
                .andExpect(status().isForbidden());
    }

    private String bankOnboardingBody(String bankCode) {
        return """
                {
                  "bankCode": "%s",
                  "bankName": "Auth Test Bank",
                  "connectorName": "MOCK_%s_CONNECTOR",
                  "connectorType": "MOCK",
                  "endpointUrl": null,
                  "timeoutMs": 5000,
                  "generateRoutes": false
                }
                """.formatted(bankCode, bankCode);
    }
}
