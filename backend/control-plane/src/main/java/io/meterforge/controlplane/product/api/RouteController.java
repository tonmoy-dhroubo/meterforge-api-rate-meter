package io.meterforge.controlplane.product.api;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.product.api.dto.CreateRouteRequest;
import io.meterforge.controlplane.product.api.dto.RouteResponse;
import io.meterforge.controlplane.product.api.dto.UpdateRouteRequest;
import io.meterforge.controlplane.product.application.RouteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/products/{productId}/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ResponseEntity<List<RouteResponse>> listRoutes(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        List<RouteResponse> routes = routeService.listRoutes(workspaceId, productId, principal.userId());
        return ResponseEntity.ok(routes);
    }

    @PostMapping
    public ResponseEntity<RouteResponse> createRoute(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateRouteRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        RouteResponse response = routeService.createRoute(workspaceId, productId, request, principal.userId(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{routeId}")
    public ResponseEntity<RouteResponse> updateRoute(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @PathVariable UUID routeId,
            @Valid @RequestBody UpdateRouteRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        RouteResponse response = routeService.updateRoute(workspaceId, productId, routeId, request, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{routeId}/activate")
    public ResponseEntity<RouteResponse> activateRoute(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @PathVariable UUID routeId,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        RouteResponse response = routeService.setRouteStatus(workspaceId, productId, routeId, ResourceStatus.ACTIVE, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{routeId}/disable")
    public ResponseEntity<RouteResponse> disableRoute(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @PathVariable UUID routeId,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        RouteResponse response = routeService.setRouteStatus(workspaceId, productId, routeId, ResourceStatus.DISABLED, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    private void requireAuth(StaffPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
