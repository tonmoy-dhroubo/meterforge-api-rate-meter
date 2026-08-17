package io.meterforge.controlplane.common.exception;

public class ResourceConflictException extends MeterForgeException {
    public ResourceConflictException(String message) {
        super(message, "RESOURCE_CONFLICT", 409);
    }
}
