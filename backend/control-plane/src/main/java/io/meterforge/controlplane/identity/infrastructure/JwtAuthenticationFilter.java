package io.meterforge.controlplane.identity.infrastructure;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final String cookieName;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            @Value("${meterforge.jwt.cookie-name:mf_session}") String cookieName
    ) {
        this.jwtTokenService = jwtTokenService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null) {
            Optional<DecodedJWT> decodedOpt = jwtTokenService.verifyToken(token);
            if (decodedOpt.isPresent()) {
                DecodedJWT jwt = decodedOpt.get();
                try {
                    UUID userId = UUID.fromString(jwt.getSubject());
                    String email = jwt.getClaim("email").asString();
                    StaffPrincipal principal = new StaffPrincipal(userId, email);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID in subject, skip authentication
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Check HttpOnly Cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        // 2. Check Authorization Bearer header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isBlank()) {
                return token;
            }
        }

        return null;
    }
}
