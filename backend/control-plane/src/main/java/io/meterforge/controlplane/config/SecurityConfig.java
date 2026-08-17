package io.meterforge.controlplane.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.controlplane.common.api.ProblemDetailResponse;
import io.meterforge.controlplane.identity.infrastructure.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/problem+json");
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            ProblemDetailResponse body = ProblemDetailResponse.of(
                    "Unauthorized",
                    HttpStatus.UNAUTHORIZED.value(),
                    "Full authentication is required to access this resource",
                    "UNAUTHORIZED",
                    requestId
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            ProblemDetailResponse body = ProblemDetailResponse.of(
                    "Forbidden",
                    HttpStatus.FORBIDDEN.value(),
                    "Access is denied",
                    "FORBIDDEN",
                    requestId
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }
}
