package io.meterforge.controlplane.product.domain;

import io.meterforge.controlplane.common.exception.RouteAmbiguityException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RouteAmbiguityValidator {

    public void validateNoAmbiguity(
            UUID currentRouteId,
            String httpMethod,
            String pathPattern,
            int priority,
            List<ApiRoute> existingRoutes
    ) {
        RoutePathPattern candidatePattern = RoutePathPattern.parse(pathPattern);
        String candidateSignature = candidatePattern.getCanonicalSignature();
        String normalizedMethod = httpMethod.trim().toUpperCase();

        for (ApiRoute existing : existingRoutes) {
            // Skip the same route when updating
            if (currentRouteId != null && existing.getId().equals(currentRouteId)) {
                continue;
            }

            if (!existing.getHttpMethod().equalsIgnoreCase(normalizedMethod)) {
                continue;
            }

            RoutePathPattern existingPattern = RoutePathPattern.parse(existing.getPathPattern());
            String existingSignature = existingPattern.getCanonicalSignature();

            // 1. Structural equivalence check (e.g. /v1/{city} vs /v1/{id})
            if (candidateSignature.equals(existingSignature)) {
                throw new RouteAmbiguityException(
                        "Route pattern '" + pathPattern + "' with method " + normalizedMethod +
                        " is structurally ambiguous with existing route '" + existing.getPathPattern() + "'"
                );
            }
        }
    }
}
