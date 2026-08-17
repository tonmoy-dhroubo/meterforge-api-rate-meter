package io.meterforge.controlplane.identity.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final String ISSUER = "meterforge-control-plane";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${meterforge.jwt.secret:change-me-to-a-random-secret-at-least-32-chars}") String secret,
            @Value("${meterforge.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    public Optional<DecodedJWT> verifyToken(String token) {
        try {
            return Optional.of(verifier.verify(token));
        } catch (JWTVerificationException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
