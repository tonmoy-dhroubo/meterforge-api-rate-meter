package io.meterforge.controlplane.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateProductRequest(
        @NotBlank(message = "Product name is required")
        String name,

        @NotBlank(message = "Product slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        String slug,

        @NotBlank(message = "Upstream base URL is required")
        String upstreamBaseUrl,

        @NotBlank(message = "Gateway base path is required")
        String gatewayBasePath
) {}
