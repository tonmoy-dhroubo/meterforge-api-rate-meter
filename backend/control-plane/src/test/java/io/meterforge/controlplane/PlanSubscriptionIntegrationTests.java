package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import io.meterforge.controlplane.plan.api.dto.CreateLimitPolicyRequest;
import io.meterforge.controlplane.plan.api.dto.CreatePlanRequest;
import io.meterforge.controlplane.subscription.api.dto.CreateSubscriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class PlanSubscriptionIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String SEEDED_PRODUCT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String SEEDED_APP_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
    private static final String SEEDED_PLAN_ID = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getCookie("mf_session").getValue();
    }

    @Test
    @DisplayName("Create plan with token bucket rate limit and fixed window daily quota")
    void testCreatePlanWithPolicies() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        CreatePlanRequest req = new CreatePlanRequest(
                UUID.fromString(SEEDED_PRODUCT_ID),
                "Pro Tier " + suffix,
                "pro-" + suffix,
                List.of(
                        new CreateLimitPolicyRequest(null, LimitPolicyKind.RATE, 50, 50, 1, null, null),
                        new CreateLimitPolicyRequest(null, LimitPolicyKind.QUOTA, null, null, null, 10000L, QuotaPeriod.DAY)
                )
        );

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Pro Tier " + suffix)))
                .andExpect(jsonPath("$.policies", hasSize(2)))
                .andExpect(jsonPath("$.policies[0].kind", is("RATE")))
                .andExpect(jsonPath("$.policies[0].capacity", is(50)))
                .andExpect(jsonPath("$.policies[1].kind", is("QUOTA")))
                .andExpect(jsonPath("$.policies[1].quotaLimit", is(10000)));
    }

    @Test
    @DisplayName("Duplicate active subscription for same application and product is rejected with 409")
    void testDuplicateActiveSubscriptionRejection() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");

        // Seed data already has an active subscription for SEEDED_APP_ID and SEEDED_PRODUCT_ID
        CreateSubscriptionRequest req = new CreateSubscriptionRequest(
                UUID.fromString(SEEDED_APP_ID),
                UUID.fromString(SEEDED_PRODUCT_ID),
                UUID.fromString(SEEDED_PLAN_ID)
        );

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESOURCE_CONFLICT")));
    }
}
