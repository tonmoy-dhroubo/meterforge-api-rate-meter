package io.meterforge.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092"
})
@Testcontainers
class WorkerApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.0-alpine"))
        .withExposedPorts(6379);

    @Test
    void contextLoads() {
    }
}
