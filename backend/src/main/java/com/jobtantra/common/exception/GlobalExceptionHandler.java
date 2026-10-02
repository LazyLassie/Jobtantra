package com.jobtantra.common.exception;

import com.jobtantra.application.job.InvalidJobRequestException;
import com.jobtantra.application.job.JobStateException;
import com.jobtantra.application.execution.ExecutionStateException;
import com.jobtantra.application.execution.MissingIdempotencyKeyException;
import com.jobtantra.application.schedule.InvalidScheduleException;
import com.jobtantra.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(), (first, second) -> first));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<ApiError> handleInvalidJobRequest(InvalidJobRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_JOB_REQUEST", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(JobStateException.class)
    public ResponseEntity<ApiError> handleJobState(JobStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_JOB_STATE", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ExecutionStateException.class)
    public ResponseEntity<ApiError> handleExecutionState(ExecutionStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_EXECUTION_STATE", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleMissingIdempotencyKey(MissingIdempotencyKeyException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidScheduleException.class)
    public ResponseEntity<ApiError> handleInvalidSchedule(InvalidScheduleException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled exception processing {} {} (correlationId={})", request.getMethod(),
                request.getRequestURI(), MDC.get("correlationId"), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(code, message, request.getRequestURI(), MDC.get("correlationId"), Instant.now(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
