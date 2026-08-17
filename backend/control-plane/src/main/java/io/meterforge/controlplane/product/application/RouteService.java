package io.meterforge.controlplane.product.application;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.common.Role;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.RouteConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.product.api.dto.CreateRouteRequest;
import io.meterforge.controlplane.product.api.dto.RouteResponse;
import io.meterforge.controlplane.product.api.dto.UpdateRouteRequest;
import io.meterforge.controlplane.product.domain.ApiProduct;
import io.meterforge.controlplane.product.domain.ApiProductRepository;
import io.meterforge.controlplane.product.domain.ApiRoute;
import io.meterforge.controlplane.product.domain.ApiRouteRepository;
import io.meterforge.controlplane.product.domain.RouteAmbiguityValidator;
import io.meterforge.controlplane.product.domain.RoutePathPattern;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RouteService {

    private final ApiProductRepository productRepository;
    private final ApiRouteRepository routeRepository;
    private final RouteAmbiguityValidator ambiguityValidator;
    private final WorkspaceSecurityEvaluator securityEvaluator;
    private final TransactionalMutationService mutationService;

    public RouteService(
            ApiProductRepository productRepository,
            ApiRouteRepository routeRepository,
            RouteAmbiguityValidator ambiguityValidator,
            WorkspaceSecurityEvaluator securityEvaluator,
            TransactionalMutationService mutationService
    ) {
        this.productRepository = productRepository;
        this.routeRepository = routeRepository;
        this.ambiguityValidator = ambiguityValidator;
        this.securityEvaluator = securityEvaluator;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(UUID workspaceId, UUID productId, UUID userId) {
        securityEvaluator.requireMembership(workspaceId, userId);
        productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API Product not found"));

        return routeRepository.findByWorkspaceIdAndProductIdOrderByPriorityDescCreatedAtAsc(workspaceId, productId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public RouteResponse createRoute(UUID workspaceId, UUID productId, CreateRouteRequest request, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        ApiProduct product = productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API Product not found"));

        String method = request.httpMethod().trim().toUpperCase();
        RoutePathPattern parsedPattern = RoutePathPattern.parse(request.pathPattern());
        String normalizedPattern = parsedPattern.getRawPattern();
        int costUnits = request.costUnits() != null ? request.costUnits() : 1;
        int priority = request.priority() != null ? request.priority() : 0;

        if (routeRepository.existsByProductIdAndHttpMethodAndPathPattern(productId, method, normalizedPattern)) {
            throw new ResourceConflictException("Route with method " + method + " and pattern '" + normalizedPattern + "' already exists for this product");
        }

        List<ApiRoute> existingRoutes = routeRepository.findByProductId(productId);
        ambiguityValidator.validateNoAmbiguity(null, method, normalizedPattern, priority, existingRoutes);

        UUID routeId = UUID.randomUUID();
        ApiRoute route = new ApiRoute(
                routeId,
                workspaceId,
                productId,
                method,
                normalizedPattern,
                request.upstreamPath(),
                costUnits,
                priority,
                ResourceStatus.ACTIVE
        );
        routeRepository.save(route);

        ConfigEventEnvelope<RouteConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ROUTE",
                routeId,
                route.getVersion(),
                "RouteConfigurationChangedV1",
                new RouteConfigurationChangedV1(
                        routeId,
                        productId,
                        workspaceId,
                        route.getHttpMethod(),
                        route.getPathPattern(),
                        route.getUpstreamPath(),
                        route.getCostUnits(),
                        route.getPriority(),
                        route.getStatus(),
                        route.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ROUTE_CREATED",
                "ROUTE",
                routeId,
                requestId,
                "Route '" + method + " " + normalizedPattern + "' created on product '" + product.getName() + "'",
                Map.of("method", method, "pathPattern", normalizedPattern, "costUnits", costUnits, "priority", priority),
                outboxEvent
        );

        return mapToResponse(route);
    }

    @Transactional
    public RouteResponse updateRoute(UUID workspaceId, UUID productId, UUID routeId, UpdateRouteRequest request, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        ApiRoute route = routeRepository.findByIdAndWorkspaceIdAndProductId(routeId, workspaceId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        route.update(request.upstreamPath(), request.costUnits(), request.priority());
        routeRepository.save(route);

        ConfigEventEnvelope<RouteConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ROUTE",
                routeId,
                route.getVersion(),
                "RouteConfigurationChangedV1",
                new RouteConfigurationChangedV1(
                        routeId,
                        productId,
                        workspaceId,
                        route.getHttpMethod(),
                        route.getPathPattern(),
                        route.getUpstreamPath(),
                        route.getCostUnits(),
                        route.getPriority(),
                        route.getStatus(),
                        route.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ROUTE_UPDATED",
                "ROUTE",
                routeId,
                requestId,
                "Route '" + route.getHttpMethod() + " " + route.getPathPattern() + "' updated",
                Map.of("costUnits", route.getCostUnits(), "priority", route.getPriority()),
                outboxEvent
        );

        return mapToResponse(route);
    }

    @Transactional
    public RouteResponse setRouteStatus(UUID workspaceId, UUID productId, UUID routeId, ResourceStatus status, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        ApiRoute route = routeRepository.findByIdAndWorkspaceIdAndProductId(routeId, workspaceId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        route.setStatus(status);
        routeRepository.save(route);

        ConfigEventEnvelope<RouteConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ROUTE",
                routeId,
                route.getVersion(),
                "RouteConfigurationChangedV1",
                new RouteConfigurationChangedV1(
                        routeId,
                        productId,
                        workspaceId,
                        route.getHttpMethod(),
                        route.getPathPattern(),
                        route.getUpstreamPath(),
                        route.getCostUnits(),
                        route.getPriority(),
                        route.getStatus(),
                        route.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ROUTE_STATUS_CHANGED",
                "ROUTE",
                routeId,
                requestId,
                "Route '" + route.getHttpMethod() + " " + route.getPathPattern() + "' status changed to " + status,
                Map.of("status", status),
                outboxEvent
        );

        return mapToResponse(route);
    }

    private RouteResponse mapToResponse(ApiRoute route) {
        return new RouteResponse(
                route.getId(),
                route.getWorkspaceId(),
                route.getProductId(),
                route.getHttpMethod(),
                route.getPathPattern(),
                route.getUpstreamPath(),
                route.getCostUnits(),
                route.getPriority(),
                route.getStatus(),
                route.getCreatedAt(),
                route.getUpdatedAt(),
                route.getVersion()
        );
    }
}
