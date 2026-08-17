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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class AuthIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Login with valid credentials returns 200, JWT token, user profile, and sets HttpOnly cookie")
    void testValidLogin() throws Exception {
        LoginRequest request = new LoginRequest("owner@meterforge.local", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("mf_session"))
                .andExpect(cookie().httpOnly("mf_session", true))
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.user.email", is("owner@meterforge.local")))
                .andExpect(jsonPath("$.user.workspaces", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.user.workspaces[0].slug", is("acme-apis")))
                .andExpect(jsonPath("$.user.workspaces[0].role", is("OWNER")));
    }

    @Test
    @DisplayName("Login with invalid password returns 401 with problem detail")
    void testInvalidPassword() throws Exception {
        LoginRequest request = new LoginRequest("owner@meterforge.local", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Get /api/v1/me with valid Bearer token returns profile")
    void testGetMeAuthenticated() throws Exception {
        LoginRequest request = new LoginRequest("owner@meterforge.local", "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("owner@meterforge.local")))
                .andExpect(jsonPath("$.workspaces[0].slug", is("acme-apis")));
    }

    @Test
    @DisplayName("Get /api/v1/me without token returns 401 problem detail")
    void testGetMeUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Logout clears the session cookie")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("mf_session"))
                .andExpect(cookie().maxAge("mf_session", 0));
    }
}
