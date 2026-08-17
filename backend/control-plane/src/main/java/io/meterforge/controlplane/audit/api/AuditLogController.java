package io.meterforge.controlplane.audit.api;

import io.meterforge.controlplane.audit.domain.AuditLog;
import io.meterforge.controlplane.audit.domain.AuditLogRepository;
import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public record AuditLogDto(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String action,
            String resourceType,
            UUID resourceId,
            String requestId,
            String summary,
            Map<String, Object> metadata,
            Instant createdAt
    ) {}

    public AuditLogController(AuditLogRepository auditLogRepository, WorkspaceSecurityEvaluator securityEvaluator) {
        this.auditLogRepository = auditLogRepository;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> listAuditLogs(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        securityEvaluator.requireMembership(workspaceId, principal.userId());

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<AuditLogDto> dtos = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(this::mapToDto);

        return ResponseEntity.ok(dtos);
    }

    private AuditLogDto mapToDto(AuditLog a) {
        return new AuditLogDto(
                a.getId(),
                a.getWorkspaceId(),
                a.getUserId(),
                a.getAction(),
                a.getResourceType(),
                a.getResourceId(),
                a.getRequestId(),
                a.getSummary(),
                a.getMetadata(),
                a.getCreatedAt()
        );
    }
}
