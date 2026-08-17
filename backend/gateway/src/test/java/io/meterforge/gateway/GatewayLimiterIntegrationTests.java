package io.meterforge.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.contracts.projection.PolicyProjection;
import io.meterforge.contracts.projection.ProductProjection;
import io.meterforge.contracts.projection.RouteProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import io.meterforge.gateway.config.MeterForgeGatewayProperties;
import io.meterforge.gateway.credential.ApiKeyAuthenticator;
import io.meterforge.gateway.routing.ProductRouteMatcher;
import io.meterforge.gateway.routing.SubscriptionResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GatewayLimiterIntegrationTests {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.0-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterForgeGatewayProperties gatewayProperties;

    @Autowired
    private ApiKeyAuthenticator apiKeyAuthenticator;

    @Autowired
    private ProductRouteMatcher productRouteMatcher;

    @Autowired
    private SubscriptionResolver subscriptionResolver;

    private UUID workspaceId;
    private UUID productId;
    private UUID routeId;
    private UUID consumerId;
    private UUID appId;
    private UUID credId;
    private String publicId;
    private String rawSecret;
    private String fullApiKey;
    private String secretHmac;
    private UUID planId;
    private UUID subId;
    private UUID ratePolicyId;

    @BeforeEach
    void setUp() throws Exception {
        // Flush Redis to ensure isolated test state
        redisTemplate.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();

        apiKeyAuthenticator.clearCache();
        productRouteMatcher.clearCache();
        subscriptionResolver.clearCache();

        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();

        workspaceId = UUID.randomUUID();
        productId = UUID.randomUUID();
        routeId = UUID.randomUUID();
        consumerId = UUID.randomUUID();
        appId = UUID.randomUUID();
        credId = UUID.randomUUID();
        publicId = "pub" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        rawSecret = "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        fullApiKey = "mf_dev_" + publicId + "_" + rawSecret;
        secretHmac = computeHmac(gatewayProperties.getApiKeyPepper(), fullApiKey);

        planId = UUID.randomUUID();
        subId = UUID.randomUUID();
        ratePolicyId = UUID.randomUUID();

        // 1. Project Credential
        CredentialProjection credProj = new CredentialProjection(
                credId, workspaceId, appId, publicId, secretHmac, "dev", ResourceStatus.ACTIVE, null, null, 1L
        );
        redisTemplate.opsForValue().set("rf:v1:cfg:credential:" + publicId, objectMapper.writeValueAsString(credProj)).block();

        // 2. Project Product & Route
        RouteProjection routeProj = new RouteProjection(
                routeId, "GET", "/v1/forecast/{city}", null, 1, 100, ResourceStatus.ACTIVE, 1L
        );
        ProductProjection prodProj = new ProductProjection(
                productId, workspaceId, "Weather API", "weather-api",
                "http://localhost:" + wireMockServer.port(), "/v1/forecast",
                ResourceStatus.ACTIVE, List.of(routeProj), 1L
        );
        redisTemplate.opsForValue().set("rf:v1:cfg:product:" + productId, objectMapper.writeValueAsString(prodProj)).block();

        // 3. Project Policy & Subscription
        PolicyProjection ratePolicy = new PolicyProjection(
                ratePolicyId, null, LimitPolicyKind.RATE, 5, 5, 10, null, null, true
        );
        SubscriptionProjection subProj = new SubscriptionProjection(
                subId, workspaceId, appId, productId, planId, ResourceStatus.ACTIVE, List.of(ratePolicy), null, null, 1L
        );
        redisTemplate.opsForValue().set("rf:v1:cfg:subscription:" + subId, objectMapper.writeValueAsString(subProj)).block();
        redisTemplate.opsForValue().set("rf:v1:cfg:app-sub:" + appId + ":" + productId, subId.toString()).block();

        // WireMock stub
        wireMockServer.stubFor(get(urlPathMatching("/v1/forecast/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"city\":\"tokyo\",\"temperature\":22}")));
    }

    @Test
    @DisplayName("Single valid request is authenticated, rate checked, stripped of credentials, and proxied to WireMock")
    void testSuccessfulProxyAndAllowedEvent() {
        webTestClient.get()
                .uri("/v1/forecast/tokyo")
                .header("X-API-Key", fullApiKey)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "4")
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.city").isEqualTo("tokyo")
                .jsonPath("$.temperature").isEqualTo(22);

        // Verify WireMock received request without X-API-Key
        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/forecast/tokyo"))
                .withoutHeader("X-API-Key")
                .withHeader("X-Request-ID", matching(".+")));
    }

    @Test
    @DisplayName("Burst of 10 concurrent requests against 5-token bucket yields exactly 5 allowed (200) and 5 limited (429)")
    void testBurstRateLimitingAndRefill() {
        int totalRequests = 10;
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger limitedCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                webTestClient.get()
                        .uri("/v1/forecast/london")
                        .header("X-API-Key", fullApiKey)
                        .exchange()
                        .expectBody()
                        .consumeWith(result -> {
                            int status = result.getStatus().value();
                            if (status == 200) {
                                okCount.incrementAndGet();
                            } else if (status == 429) {
                                limitedCount.incrementAndGet();
                            }
                        });
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertThat(okCount.get())
                .as("Exactly capacity (5) requests should succeed")
                .isEqualTo(5);

        assertThat(limitedCount.get())
                .as("Remaining requests (5) must be 429 Too Many Requests")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Missing, invalid HMAC, or revoked API key returns HTTP 401 Unauthorized")
    void testUnauthorizedInvalidOrRevokedKey() throws Exception {
        // 1. Missing key
        webTestClient.get()
                .uri("/v1/forecast/berlin")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIAL");

        // 2. Tampered / wrong secret key
        String tamperedKey = "mf_dev_" + publicId + "_wrongsecret123456";
        webTestClient.get()
                .uri("/v1/forecast/berlin")
                .header("X-API-Key", tamperedKey)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIAL");

        // 3. Revoked key in Redis (status = DISABLED or revokedAt set)
        CredentialProjection revoked = new CredentialProjection(
                credId, workspaceId, appId, publicId, secretHmac, "dev", ResourceStatus.DISABLED, null, Instant.now(), 2L
        );
        redisTemplate.opsForValue().set("rf:v1:cfg:credential:" + publicId, objectMapper.writeValueAsString(revoked)).block();
        apiKeyAuthenticator.clearCache();

        webTestClient.get()
                .uri("/v1/forecast/berlin")
                .header("X-API-Key", fullApiKey)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Quota policy allowance enforcement returns 429 when limit is exceeded")
    void testQuotaAllowanceEnforcement() throws Exception {
        UUID quotaSubId = UUID.randomUUID();
        UUID quotaPolicyId = UUID.randomUUID();

        PolicyProjection quotaPolicy = new PolicyProjection(
                quotaPolicyId, null, LimitPolicyKind.QUOTA, null, null, null, 2L, QuotaPeriod.DAY, true
        );
        SubscriptionProjection quotaSub = new SubscriptionProjection(
                quotaSubId, workspaceId, appId, productId, planId, ResourceStatus.ACTIVE, List.of(quotaPolicy), null, null, 1L
        );
        redisTemplate.opsForValue().set("rf:v1:cfg:subscription:" + quotaSubId, objectMapper.writeValueAsString(quotaSub)).block();
        redisTemplate.opsForValue().set("rf:v1:cfg:app-sub:" + appId + ":" + productId, quotaSubId.toString()).block();

        // Request 1: OK (1 used)
        webTestClient.get()
                .uri("/v1/forecast/rome")
                .header("X-API-Key", fullApiKey)
                .exchange()
                .expectStatus().isOk();

        // Request 2: OK (2 used - limit reached)
        webTestClient.get()
                .uri("/v1/forecast/rome")
                .header("X-API-Key", fullApiKey)
                .exchange()
                .expectStatus().isOk();

        // Request 3: Exceeded -> 429
        webTestClient.get()
                .uri("/v1/forecast/rome")
                .header("X-API-Key", fullApiKey)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("X-RateLimit-Reset")
                .expectBody()
                .jsonPath("$.code").isEqualTo("RATE_LIMITED");
    }

    private String computeHmac(String pepper, String rawKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }
}
