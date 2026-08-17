package io.meterforge.controlplane.common.exception;

import java.util.List;

public class InvalidInputException extends MeterForgeException {
    private final List<FieldError> fieldErrors;

    public record FieldError(String field, String message) {}

    public InvalidInputException(String message) {
        super(message, "INVALID_INPUT", 400);
        this.fieldErrors = List.of();
    }

    public InvalidInputException(String message, String code) {
        super(message, code, 400);
        this.fieldErrors = List.of();
    }

    public InvalidInputException(String message, String code, List<FieldError> fieldErrors) {
        super(message, code, 400);
        this.fieldErrors = fieldErrors != null ? fieldErrors : List.of();
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
