package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class UsageAnalyticsIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String WEATHER_PRODUCT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String WEATHER_ROUTE_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String NORTHSTAR_CONSUMER_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String NORTHSTAR_APP_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        jdbcTemplate.execute("DELETE FROM meterforge.usage_events");
        jdbcTemplate.execute("DELETE FROM meterforge.usage_hourly");
        jdbcTemplate.execute("DELETE FROM meterforge.usage_daily");
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
    @DisplayName("Usage summary accurately aggregates allowed, rate-limited, and error requests")
    void testUsageSummaryAggregation() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");
        Instant now = Instant.now();
        Instant currentHour = now.truncatedTo(ChronoUnit.HOURS);

        // Seed hourly rollups: 10 allowed (2xx), 5 limited (4xx), 2 server errors (5xx)
        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_hourly
            (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class, total_requests, total_units, total_latency_ms)
            VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, 'ALLOWED', '2xx', 10, 10, 250)
        """, Timestamp.from(currentHour), ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_hourly
            (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class, total_requests, total_units, total_latency_ms)
            VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, 'RATE_LIMITED', '4xx', 5, 0, 15)
        """, Timestamp.from(currentHour), ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_hourly
            (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class, total_requests, total_units, total_latency_ms)
            VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, 'ALLOWED', '5xx', 2, 2, 80)
        """, Timestamp.from(currentHour), ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(17)))
                .andExpect(jsonPath("$.allowedRequests", is(12)))
                .andExpect(jsonPath("$.rateLimitedRequests", is(5)))
                .andExpect(jsonPath("$.serverErrorRequests", is(2)))
                .andExpect(jsonPath("$.totalUnitsConsumed", is(12)));
    }

    @Test
    @DisplayName("Usage timeseries returns bucketed metrics over requested interval")
    void testUsageTimeseriesEndpoint() throws Exception {
        String token = loginAndGetToken("viewer@meterforge.local", "password123");
        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_hourly
            (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class, total_requests, total_units, total_latency_ms)
            VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, 'ALLOWED', '2xx', 25, 25, 500)
        """, Timestamp.from(currentHour), ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/timeseries")
                        .header("Authorization", "Bearer " + token)
                        .param("granularity", "HOUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity", is("HOUR")))
                .andExpect(jsonPath("$.buckets", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.buckets[0].totalRequests", is(25)))
                .andExpect(jsonPath("$.buckets[0].allowedRequests", is(25)));
    }

    @Test
    @DisplayName("Top routes and top applications return seeded entities ranked by request volume")
    void testTopRoutesAndApplications() throws Exception {
        String token = loginAndGetToken("member@meterforge.local", "password123");
        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_hourly
            (bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class, total_requests, total_units, total_latency_ms)
            VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, 'ALLOWED', '2xx', 42, 42, 600)
        """, Timestamp.from(currentHour), ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/top-routes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pathPattern", is("/v1/forecast/{city}")))
                .andExpect(jsonPath("$[0].totalRequests", is(42)));

        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/top-applications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationName", is("Northstar Demo App")))
                .andExpect(jsonPath("$[0].totalRequests", is(42)));
    }

    @Test
    @DisplayName("Raw usage events endpoint supports filtering by decision and pagination")
    void testRawUsageEventsListAndDetail() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO meterforge.usage_events
            (event_id, occurred_at, received_at, workspace_id, product_id, route_id, consumer_id, application_id, credential_id, subscription_id, request_id, http_method, route_template, decision, outcome, status_code, usage_units, latency_ms, limiting_policy_id, gateway_instance_id)
            VALUES (?::uuid, NOW(), NOW(), ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, null, null, 'req-m4-test-1', 'GET', '/v1/forecast/{city}', 'ALLOWED', 'SUCCESS', 200, 1, 18, null, 'gateway-1')
        """, eventId, ACME_WORKSPACE_ID, WEATHER_PRODUCT_ID, WEATHER_ROUTE_ID, NORTHSTAR_CONSUMER_ID, NORTHSTAR_APP_ID);

        // 1. List events
        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/events")
                        .header("Authorization", "Bearer " + token)
                        .param("decision", "ALLOWED")
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].requestId", is("req-m4-test-1")))
                .andExpect(jsonPath("$.items[0].statusCode", is(200)));

        // 2. Get event by ID
        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/usage/events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId.toString())))
                .andExpect(jsonPath("$.routeTemplate", is("/v1/forecast/{city}")));
    }
}
