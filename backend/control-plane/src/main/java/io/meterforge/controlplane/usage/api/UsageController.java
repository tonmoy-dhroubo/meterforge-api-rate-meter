package io.meterforge.controlplane.usage.api;

import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.usage.api.dto.*;
import io.meterforge.controlplane.usage.application.UsageService;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/usage")
public class UsageController {

    private final UsageService usageService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public UsageController(UsageService usageService, WorkspaceSecurityEvaluator securityEvaluator) {
        this.usageService = usageService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping("/summary")
    public ResponseEntity<UsageSummaryResponse> getSummary(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        UsageSummaryResponse response = usageService.getUsageSummary(workspaceId, from, to, productId, consumerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/timeseries")
    public ResponseEntity<UsageTimeseriesResponse> getTimeseries(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "HOUR") String granularity,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        UsageTimeseriesResponse response = usageService.getUsageTimeseries(workspaceId, from, to, granularity, productId, consumerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-routes")
    public ResponseEntity<List<TopRouteDto>> getTopRoutes(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "5") int limit,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        List<TopRouteDto> routes = usageService.getTopRoutes(workspaceId, from, to, limit);
        return ResponseEntity.ok(routes);
    }

    @GetMapping("/top-applications")
    public ResponseEntity<List<TopApplicationDto>> getTopApplications(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "5") int limit,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        List<TopApplicationDto> applications = usageService.getTopApplications(workspaceId, from, to, limit);
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/events")
    public ResponseEntity<RawUsageEventsPageDto> getRawEvents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        RawUsageEventsPageDto response = usageService.getRawUsageEvents(workspaceId, from, to, productId, consumerId, decision, limit, offset);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<RawUsageEventDto> getEventById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        securityEvaluator.requireMembership(workspaceId, principal.userId());
        return usageService.getUsageEventById(workspaceId, eventId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Usage event not found"));
    }

    private void requireAuth(StaffPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }
}
