package io.meterforge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisReactiveConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> rateLimiterScript() {
        ClassPathResource resource = new ClassPathResource("scripts/rate_limiter.lua");
        return RedisScript.of(resource, List.class);
    }
}
