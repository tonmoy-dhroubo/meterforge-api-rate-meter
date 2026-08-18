package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.controlplane.audit.domain.AuditLogRepository;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import io.meterforge.controlplane.outbox.domain.OutboxEventRepository;
import io.meterforge.controlplane.product.api.dto.CreateProductRequest;
import io.meterforge.controlplane.product.api.dto.CreateRouteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class ProductRouteIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String WEATHER_PRODUCT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
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
    @DisplayName("Create product creates product, records audit log, and writes transactional outbox event")
    void testCreateProductWithOutboxAndAudit() throws Exception {
        String token = loginAndGetToken("member@meterforge.local", "password123");
        String slug = "geocoding-" + UUID.randomUUID().toString().substring(0, 6);
        CreateProductRequest request = new CreateProductRequest("Geocoding API", slug, "http://wiremock:8080", "/v1/" + slug);

        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "req-test-create-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Geocoding API")))
                .andExpect(jsonPath("$.slug", is(slug)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();

        UUID productId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // Verify Outbox Event exists in DB
        assertThat(outboxEventRepository.findUnpublishedEvents(Pageable.ofSize(100)))
                .anyMatch(e -> e.getAggregateId().equals(productId) && "ProductConfigurationChangedV1".equals(e.getEventType()));

        // Verify Audit Log exists in DB
        assertThat(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(UUID.fromString(ACME_WORKSPACE_ID), Pageable.ofSize(100)))
                .anyMatch(a -> "PRODUCT_CREATED".equals(a.getAction()) && productId.equals(a.getResourceId()));
    }

    @Test
    @DisplayName("Create route with ambiguous pattern is rejected with 400 ROUTE_AMBIGUITY")
    void testAmbiguousRouteRejection() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");

        // Seeded route on Weather API is GET /v1/forecast/{city}
        // Attempting GET /v1/forecast/{location} is structurally identical and ambiguous!
        CreateRouteRequest ambiguousRequest = new CreateRouteRequest("GET", "/v1/forecast/{location}", null, 1, 10);

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products/" + WEATHER_PRODUCT_ID + "/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ambiguousRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code", is("ROUTE_AMBIGUITY")))
                .andExpect(jsonPath("$.detail", containsString("structurally ambiguous")));
    }

    @Test
    @DisplayName("Create route with invalid syntax returns 400 INVALID_PATH_PATTERN")
    void testInvalidPathPatternSyntax() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");

        // Pattern with consecutive slashes
        CreateRouteRequest invalidRequest = new CreateRouteRequest("GET", "/v1//forecast", null, 1, 0);

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products/" + WEATHER_PRODUCT_ID + "/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code", is("INVALID_PATH_PATTERN")));
    }

    @Test
    @DisplayName("List seeded products and routes returns Acme APIs Weather API")
    void testListProductsAndRoutes() throws Exception {
        String token = loginAndGetToken("viewer@meterforge.local", "password123");

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].slug", hasItem("weather-api")));

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products/" + WEATHER_PRODUCT_ID + "/routes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].pathPattern", hasItem("/v1/forecast/{city}")));
    }
}
