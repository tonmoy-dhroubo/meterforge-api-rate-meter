package io.meterforge.controlplane.product.api;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.product.api.dto.CreateProductRequest;
import io.meterforge.controlplane.product.api.dto.ProductResponse;
import io.meterforge.controlplane.product.api.dto.UpdateProductRequest;
import io.meterforge.controlplane.product.application.ProductService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        List<ProductResponse> products = productService.listProducts(workspaceId, principal.userId());
        return ResponseEntity.ok(products);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        ProductResponse response = productService.createProduct(workspaceId, request, principal.userId(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        ProductResponse response = productService.getProduct(workspaceId, productId, principal.userId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        ProductResponse response = productService.updateProduct(workspaceId, productId, request, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/activate")
    public ResponseEntity<ProductResponse> activateProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        ProductResponse response = productService.setProductStatus(workspaceId, productId, ResourceStatus.ACTIVE, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/disable")
    public ResponseEntity<ProductResponse> disableProduct(
            @PathVariable UUID workspaceId,
            @PathVariable UUID productId,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        ProductResponse response = productService.setProductStatus(workspaceId, productId, ResourceStatus.DISABLED, principal.userId(), requestId);
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
