package io.meterforge.controlplane.common.exception;

public class RouteAmbiguityException extends MeterForgeException {
    public RouteAmbiguityException(String message) {
        super(message, "ROUTE_AMBIGUITY", 400);
    }
}
