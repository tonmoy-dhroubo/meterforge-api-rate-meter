package io.meterforge.contracts.event;

public enum UsageOutcome {
    SUCCESS,
    CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT,
    UNAVAILABLE,
    NOT_FORWARDED
}
