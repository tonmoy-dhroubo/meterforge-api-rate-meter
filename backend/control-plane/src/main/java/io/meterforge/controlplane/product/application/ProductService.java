package io.meterforge.controlplane.product.application;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.common.Role;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.ProductConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.product.api.dto.CreateProductRequest;
import io.meterforge.controlplane.product.api.dto.ProductResponse;
import io.meterforge.controlplane.product.api.dto.UpdateProductRequest;
import io.meterforge.controlplane.product.domain.ApiProduct;
import io.meterforge.controlplane.product.domain.ApiProductRepository;
import io.meterforge.controlplane.product.domain.ApiRouteRepository;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final ApiProductRepository productRepository;
    private final ApiRouteRepository routeRepository;
    private final WorkspaceSecurityEvaluator securityEvaluator;
    private final TransactionalMutationService mutationService;

    public ProductService(
            ApiProductRepository productRepository,
            ApiRouteRepository routeRepository,
            WorkspaceSecurityEvaluator securityEvaluator,
            TransactionalMutationService mutationService
    ) {
        this.productRepository = productRepository;
        this.routeRepository = routeRepository;
        this.securityEvaluator = securityEvaluator;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts(UUID workspaceId, UUID userId) {
        securityEvaluator.requireMembership(workspaceId, userId);
        List<ApiProduct> products = productRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return products.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID workspaceId, UUID productId, UUID userId) {
        securityEvaluator.requireMembership(workspaceId, userId);
        ApiProduct product = productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API Product not found"));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(UUID workspaceId, CreateProductRequest request, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        String slug = request.slug().trim().toLowerCase();
        if (productRepository.existsByWorkspaceIdAndSlug(workspaceId, slug)) {
            throw new ResourceConflictException("Product with slug '" + slug + "' already exists in this workspace");
        }

        String basePath = ApiProduct.normalizeBasePath(request.gatewayBasePath());
        if (productRepository.existsByWorkspaceIdAndGatewayBasePath(workspaceId, basePath)) {
            throw new ResourceConflictException("Product with gateway base path '" + basePath + "' already exists in this workspace");
        }

        UUID productId = UUID.randomUUID();
        ApiProduct product = new ApiProduct(
                productId,
                workspaceId,
                request.name(),
                slug,
                request.upstreamBaseUrl(),
                basePath,
                ResourceStatus.ACTIVE
        );
        product = productRepository.saveAndFlush(product);

        ConfigEventEnvelope<ProductConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "PRODUCT",
                productId,
                product.getVersion(),
                "ProductConfigurationChangedV1",
                new ProductConfigurationChangedV1(
                        productId,
                        workspaceId,
                        product.getName(),
                        product.getSlug(),
                        product.getUpstreamBaseUrl(),
                        product.getGatewayBasePath(),
                        product.getStatus(),
                        product.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "PRODUCT_CREATED",
                "PRODUCT",
                productId,
                requestId,
                "Product '" + product.getName() + "' created",
                Map.of("name", product.getName(), "slug", product.getSlug(), "basePath", product.getGatewayBasePath()),
                outboxEvent
        );

        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID workspaceId, UUID productId, UpdateProductRequest request, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        ApiProduct product = productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API Product not found"));

        if (request.gatewayBasePath() != null && !request.gatewayBasePath().isBlank()) {
            String newBasePath = ApiProduct.normalizeBasePath(request.gatewayBasePath());
            if (!newBasePath.equals(product.getGatewayBasePath()) && productRepository.existsByWorkspaceIdAndGatewayBasePath(workspaceId, newBasePath)) {
                throw new ResourceConflictException("Product with gateway base path '" + newBasePath + "' already exists in this workspace");
            }
        }

        product.update(request.name(), request.upstreamBaseUrl(), request.gatewayBasePath());
        product = productRepository.saveAndFlush(product);

        ConfigEventEnvelope<ProductConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "PRODUCT",
                productId,
                product.getVersion(),
                "ProductConfigurationChangedV1",
                new ProductConfigurationChangedV1(
                        productId,
                        workspaceId,
                        product.getName(),
                        product.getSlug(),
                        product.getUpstreamBaseUrl(),
                        product.getGatewayBasePath(),
                        product.getStatus(),
                        product.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "PRODUCT_UPDATED",
                "PRODUCT",
                productId,
                requestId,
                "Product '" + product.getName() + "' updated",
                Map.of("name", product.getName(), "slug", product.getSlug()),
                outboxEvent
        );

        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse setProductStatus(UUID workspaceId, UUID productId, ResourceStatus status, UUID userId, String requestId) {
        securityEvaluator.requireRole(workspaceId, userId, Role.MEMBER);

        ApiProduct product = productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API Product not found"));

        product.setStatus(status);
        product = productRepository.saveAndFlush(product);

        ConfigEventEnvelope<ProductConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "PRODUCT",
                productId,
                product.getVersion(),
                "ProductConfigurationChangedV1",
                new ProductConfigurationChangedV1(
                        productId,
                        workspaceId,
                        product.getName(),
                        product.getSlug(),
                        product.getUpstreamBaseUrl(),
                        product.getGatewayBasePath(),
                        product.getStatus(),
                        product.getVersion()
                )
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "PRODUCT_STATUS_CHANGED",
                "PRODUCT",
                productId,
                requestId,
                "Product '" + product.getName() + "' status changed to " + status,
                Map.of("status", status),
                outboxEvent
        );

        return mapToResponse(product);
    }

    private ProductResponse mapToResponse(ApiProduct product) {
        int routeCount = routeRepository.countByWorkspaceIdAndProductId(product.getWorkspaceId(), product.getId());
        return new ProductResponse(
                product.getId(),
                product.getWorkspaceId(),
                product.getName(),
                product.getSlug(),
                product.getUpstreamBaseUrl(),
                product.getGatewayBasePath(),
                product.getStatus(),
                routeCount,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion()
        );
    }
}
