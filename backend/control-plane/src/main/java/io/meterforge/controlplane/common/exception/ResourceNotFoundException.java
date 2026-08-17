package io.meterforge.controlplane.common.exception;

public class ResourceNotFoundException extends MeterForgeException {
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", 404);
    }
}
