package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.controlplane.consumer.api.dto.CreateApplicationRequest;
import io.meterforge.controlplane.consumer.api.dto.CreateConsumerRequest;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class ConsumerApplicationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

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

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    @Test
    @DisplayName("Create consumer and application, and list them in workspace")
    void testCreateConsumerAndApplication() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create consumer
        CreateConsumerRequest consumerReq = new CreateConsumerRequest("Acme Partner " + uniqueSuffix, "EXT-" + uniqueSuffix);
        MvcResult consumerResult = mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consumerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Acme Partner " + uniqueSuffix)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();

        String consumerId = objectMapper.readTree(consumerResult.getResponse().getContentAsString()).get("id").asText();

        // 2. Create application under consumer
        CreateApplicationRequest appReq = new CreateApplicationRequest("Mobile App " + uniqueSuffix);
        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers/" + consumerId + "/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Mobile App " + uniqueSuffix)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // 3. List applications by consumer
        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers/" + consumerId + "/applications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Mobile App " + uniqueSuffix)));
    }

    @Test
    @DisplayName("Duplicate consumer name is rejected with 409 Conflict")
    void testDuplicateConsumerRejection() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");
        String uniqueName = "Duplicate Test Consumer " + UUID.randomUUID().toString().substring(0, 8);

        CreateConsumerRequest req = new CreateConsumerRequest(uniqueName, "EXT-DUP");
        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second attempt with same name in same workspace
        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESOURCE_CONFLICT")));
    }

    @Test
    @DisplayName("Viewer cannot create consumer (403 Forbidden)")
    void testViewerCannotCreateConsumer() throws Exception {
        String token = loginAndGetToken("viewer@meterforge.local", "password123");
        CreateConsumerRequest req = new CreateConsumerRequest("Unauthorized Consumer", "EXT-UNAUTH");

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/consumers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }
}
