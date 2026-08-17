package io.meterforge.controlplane.product.api.dto;

public record UpdateProductRequest(
        String name,
        String upstreamBaseUrl,
        String gatewayBasePath
) {}
