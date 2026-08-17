package io.meterforge.controlplane.common.exception;

public class ForbiddenException extends MeterForgeException {
    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", 403);
    }
}
