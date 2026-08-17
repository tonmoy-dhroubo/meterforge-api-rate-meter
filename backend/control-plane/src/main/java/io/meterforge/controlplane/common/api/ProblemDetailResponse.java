package io.meterforge.controlplane.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailResponse(
        String title,
        int status,
        String detail,
        String code,
        String requestId,
        List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(String field, String message) {}

    public static ProblemDetailResponse of(String title, int status, String detail, String code, String requestId) {
        return new ProblemDetailResponse(title, status, detail, code, requestId, null);
    }

    public static ProblemDetailResponse of(String title, int status, String detail, String code, String requestId, List<FieldErrorItem> fieldErrors) {
        return new ProblemDetailResponse(title, status, detail, code, requestId, fieldErrors);
    }
}
