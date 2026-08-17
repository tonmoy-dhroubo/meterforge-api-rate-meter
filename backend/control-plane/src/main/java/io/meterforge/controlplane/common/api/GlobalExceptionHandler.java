package io.meterforge.controlplane.common.api;

import io.meterforge.controlplane.common.exception.InvalidInputException;
import io.meterforge.controlplane.common.exception.MeterForgeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(MeterForgeException.class)
    public ResponseEntity<ProblemDetailResponse> handleMeterForgeException(MeterForgeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        List<ProblemDetailResponse.FieldErrorItem> fieldErrors = null;
        if (ex instanceof InvalidInputException invalidInputEx && !invalidInputEx.getFieldErrors().isEmpty()) {
            fieldErrors = invalidInputEx.getFieldErrors().stream()
                    .map(fe -> new ProblemDetailResponse.FieldErrorItem(fe.field(), fe.message()))
                    .toList();
        }

        ProblemDetailResponse body = ProblemDetailResponse.of(
                status.getReasonPhrase(),
                status.value(),
                ex.getMessage(),
                ex.getCode(),
                requestId,
                fieldErrors
        );

        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        List<ProblemDetailResponse.FieldErrorItem> fieldErrors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ProblemDetailResponse.FieldErrorItem(error.getField(), error.getDefaultMessage()));
        }

        ProblemDetailResponse body = ProblemDetailResponse.of(
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed for request parameters",
                "VALIDATION_ERROR",
                requestId,
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("Unhandled exception for request [{}]: {}", requestId, ex.getMessage(), ex);

        ProblemDetailResponse body = ProblemDetailResponse.of(
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected server error occurred",
                "INTERNAL_SERVER_ERROR",
                requestId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
