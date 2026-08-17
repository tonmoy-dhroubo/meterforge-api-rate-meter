package io.meterforge.controlplane.common.exception;

import java.util.UUID;

public class ResourceNotFoundException extends MeterForgeException {
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", 404);
    }

    public ResourceNotFoundException(String resourceType, UUID id) {
        super(resourceType + " with id '" + id + "' not found", "RESOURCE_NOT_FOUND", 404);
    }
}
