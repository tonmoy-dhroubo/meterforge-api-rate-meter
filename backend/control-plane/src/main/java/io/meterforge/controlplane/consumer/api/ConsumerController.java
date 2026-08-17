package io.meterforge.controlplane.consumer.api;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.consumer.api.dto.ConsumerResponse;
import io.meterforge.controlplane.consumer.api.dto.CreateConsumerRequest;
import io.meterforge.controlplane.consumer.api.dto.UpdateConsumerRequest;
import io.meterforge.controlplane.consumer.application.ConsumerService;
import io.meterforge.controlplane.consumer.domain.Consumer;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/consumers")
public class ConsumerController {

    private final ConsumerService consumerService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public ConsumerController(ConsumerService consumerService, WorkspaceSecurityEvaluator securityEvaluator) {
        this.consumerService = consumerService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping
    public ResponseEntity<List<ConsumerResponse>> listConsumers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<ConsumerResponse> responses = consumerService.listConsumers(workspaceId).stream()
                .map(c -> ConsumerResponse.from(c, consumerService.getApplicationCount(workspaceId, c.getId())))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{consumerId}")
    public ResponseEntity<ConsumerResponse> getConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        Consumer consumer = consumerService.getConsumer(workspaceId, consumerId);
        long appCount = consumerService.getApplicationCount(workspaceId, consumerId);
        return ResponseEntity.ok(ConsumerResponse.from(consumer, appCount));
    }

    @PostMapping
    public ResponseEntity<ConsumerResponse> createConsumer(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateConsumerRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Consumer consumer = consumerService.createConsumer(
                workspaceId,
                principal.userId(),
                request.name(),
                request.externalReference()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsumerResponse.from(consumer, 0));
    }

    @PatchMapping("/{consumerId}")
    public ResponseEntity<ConsumerResponse> updateConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody UpdateConsumerRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Consumer consumer = consumerService.updateConsumer(
                workspaceId,
                principal.userId(),
                consumerId,
                request.name(),
                request.externalReference()
        );
        long appCount = consumerService.getApplicationCount(workspaceId, consumerId);
        return ResponseEntity.ok(ConsumerResponse.from(consumer, appCount));
    }

    @PostMapping("/{consumerId}/activate")
    public ResponseEntity<ConsumerResponse> activateConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Consumer consumer = consumerService.activateConsumer(workspaceId, principal.userId(), consumerId);
        long appCount = consumerService.getApplicationCount(workspaceId, consumerId);
        return ResponseEntity.ok(ConsumerResponse.from(consumer, appCount));
    }

    @PostMapping("/{consumerId}/disable")
    public ResponseEntity<ConsumerResponse> disableConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Consumer consumer = consumerService.disableConsumer(workspaceId, principal.userId(), consumerId);
        long appCount = consumerService.getApplicationCount(workspaceId, consumerId);
        return ResponseEntity.ok(ConsumerResponse.from(consumer, appCount));
    }
}
