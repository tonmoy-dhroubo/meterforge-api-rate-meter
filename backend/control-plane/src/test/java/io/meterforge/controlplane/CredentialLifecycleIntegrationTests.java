package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.controlplane.credential.api.dto.CreateCredentialRequest;
import io.meterforge.controlplane.credential.domain.ApiKeyGenerator;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class CredentialLifecycleIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyGenerator apiKeyGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String SEEDED_APP_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";

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
    @DisplayName("Issue API key returns raw secret once, stores HMAC-SHA256, and never exposes raw secret in list")
    void testApiKeyIssuanceAndVerification() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");

        CreateCredentialRequest req = new CreateCredentialRequest("dev", null);
        MvcResult issueResult = mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/applications/" + SEEDED_APP_ID + "/credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey", startsWith("mf_dev_")))
                .andExpect(jsonPath("$.publicId", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();

        String responseJson = issueResult.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseJson);
        String rawKey = jsonNode.get("rawKey").asText();
        String publicId = jsonNode.get("publicId").asText();
        String credId = jsonNode.get("id").asText();

        // 1. Verify that database contains the exact HMAC-SHA256 of the rawKey
        String dbHmac = jdbcTemplate.queryForObject(
                "SELECT secret_hmac FROM meterforge.api_credentials WHERE id = ?",
                String.class,
                UUID.fromString(credId)
        );
        String expectedHmac = apiKeyGenerator.computeHmac(rawKey);
        assertThat(dbHmac).isEqualTo(expectedHmac);

        // 2. Verify listing credentials returns safe masked prefix and NO rawKey
        mockMvc.perform(get("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/applications/" + SEEDED_APP_ID + "/credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].publicId", hasItem(publicId)))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());
    }

    @Test
    @DisplayName("Rotate API key revokes old key and returns new raw secret")
    void testApiKeyRotation() throws Exception {
        String token = loginAndGetToken("owner@meterforge.local", "password123");

        // Issue initial key
        CreateCredentialRequest req = new CreateCredentialRequest("prod", null);
        MvcResult issueResult = mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/applications/" + SEEDED_APP_ID + "/credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String credId = objectMapper.readTree(issueResult.getResponse().getContentAsString()).get("id").asText();

        // Rotate key
        MvcResult rotateResult = mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/credentials/" + credId + "/rotate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawKey", startsWith("mf_prod_")))
                .andExpect(jsonPath("$.id", not(is(credId))))
                .andReturn();

        // Verify old key is now DISABLED/revoked in DB
        String oldStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM meterforge.api_credentials WHERE id = ?",
                String.class,
                UUID.fromString(credId)
        );
        assertThat(oldStatus).isEqualTo("DISABLED");
    }
}
