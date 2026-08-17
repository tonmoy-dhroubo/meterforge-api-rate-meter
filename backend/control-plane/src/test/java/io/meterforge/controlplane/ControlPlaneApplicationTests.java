package io.meterforge.controlplane;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ControlPlaneApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        String generatedHash = passwordEncoder.encode("password123");
        System.out.println("GENERATED BCRYPT: " + generatedHash);
        assertThat(passwordEncoder.matches("password123", generatedHash)).isTrue();

        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT email, password_hash FROM meterforge.users");
        System.out.println("SEEDED USERS: " + users);
        for (Map<String, Object> u : users) {
            String hash = (String) u.get("password_hash");
            System.out.println("USER " + u.get("email") + " MATCHES: " + passwordEncoder.matches("password123", hash));
        }
    }
}
