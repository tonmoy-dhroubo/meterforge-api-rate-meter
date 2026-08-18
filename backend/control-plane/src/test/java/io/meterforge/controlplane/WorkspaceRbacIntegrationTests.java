package io.meterforge.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import io.meterforge.controlplane.product.api.dto.CreateProductRequest;
import io.meterforge.controlplane.workspace.api.dto.CreateWorkspaceRequest;
import io.meterforge.controlplane.workspace.api.dto.UpdateWorkspaceMemberRequest;
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
class WorkspaceRbacIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String ACME_WORKSPACE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String OWNER_USER_ID = "11111111-1111-1111-1111-111111111111";

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
    @DisplayName("Viewer cannot create products in workspace (403 Forbidden)")
    void testViewerCannotCreateProduct() throws Exception {
        String viewerToken = loginAndGetToken("viewer@meterforge.local", "password123");

        CreateProductRequest request = new CreateProductRequest("Unauthorized API", "unauth-api", "http://example.com", "/v1/unauth");

        mockMvc.perform(post("/api/v1/workspaces/" + ACME_WORKSPACE_ID + "/products")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("User cannot access a workspace they do not belong to (404 Not Found)")
    void testTenantIsolation() throws Exception {
        String ownerToken = loginAndGetToken("owner@meterforge.local", "password123");

        // Attempt to access non-existent or other workspace ID
        UUID randomWorkspaceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/workspaces/" + randomWorkspaceId + "/products")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Cannot demote the last active OWNER of a workspace (400 Bad Request)")
    void testCannotDemoteLastOwner() throws Exception {
        // Create an isolated new workspace with single owner
        String ownerToken = loginAndGetToken("owner@meterforge.local", "password123");
        CreateWorkspaceRequest wsRequest = new CreateWorkspaceRequest("Solo Workspace", "solo-ws-" + UUID.randomUUID().toString().substring(0, 8));

        MvcResult createResult = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wsRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String newWsId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Attempt to demote self to VIEWER
        UpdateWorkspaceMemberRequest updateRequest = new UpdateWorkspaceMemberRequest(Role.VIEWER, "ACTIVE");

        mockMvc.perform(patch("/api/v1/workspaces/" + newWsId + "/members/" + OWNER_USER_ID)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code", is("LAST_OWNER_PROTECTION")));
    }
}
