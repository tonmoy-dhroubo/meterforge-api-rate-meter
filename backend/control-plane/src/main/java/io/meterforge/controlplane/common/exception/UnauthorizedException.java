package io.meterforge.controlplane.common.exception;

public class UnauthorizedException extends MeterForgeException {
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED", 401);
    }
}
