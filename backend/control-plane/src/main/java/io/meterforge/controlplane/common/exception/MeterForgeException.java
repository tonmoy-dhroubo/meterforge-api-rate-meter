package io.meterforge.controlplane.common.exception;

public class MeterForgeException extends RuntimeException {
    private final String code;
    private final int status;

    public MeterForgeException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public MeterForgeException(String message, String code, int status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
