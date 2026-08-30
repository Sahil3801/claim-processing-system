package com.claim.demo.controller;

import com.claim.demo.dto.ApiErrorResponse;
import com.claim.demo.exception.ClaimNotFoundException;
import com.claim.demo.exception.DuplicateClaimException;
import com.claim.demo.exception.IdempotencyKeyConflictException;
import com.claim.demo.exception.InvalidClaimStatusException;
import com.claim.demo.exception.InvalidClaimTransitionException;
import com.claim.demo.exception.InvalidReportDateRangeException;
import com.claim.demo.exception.UnauthorizedClaimAccessException;
import com.claim.demo.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintValidation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String field = null;
            for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
                field = node.getName();
            }
            violations.putIfAbsent(field == null ? "request" : field, violation.getMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, violations);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        if ("Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())) {
            return response(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY",
                    "Idempotency-Key header is required", request);
        }
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "A required request header is missing", request);
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleClaimNotFound(
            ClaimNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidClaimTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransition(
            InvalidClaimTransitionException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "INVALID_CLAIM_TRANSITION", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateClaimException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateClaim(
            DuplicateClaimException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_CLAIM", exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyKeyConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE", exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String detail = exception.getMostSpecificCause().getMessage();
        boolean idempotencyConflict = detail != null && detail.toLowerCase().contains("idempotency");
        return response(HttpStatus.CONFLICT,
                idempotencyConflict ? "IDEMPOTENCY_KEY_REUSE" : "DATA_INTEGRITY_CONFLICT",
                idempotencyConflict
                        ? "Idempotency key has already been used for another request"
                        : "Request conflicts with existing data",
                request);
    }

    @ExceptionHandler(UnauthorizedClaimAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedAccess(
            UnauthorizedClaimAccessException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "UNAUTHORIZED_CLAIM_ACCESS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidClaimStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidClaimStatus(
            InvalidClaimStatusException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CLAIM_STATUS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidReportDateRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidReportDateRange(
            InvalidReportDateRangeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DATE_RANGE", exception.getMessage(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body or parameter is invalid", request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        return response(status, error, message, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String error, String message, HttpServletRequest request,
            Map<String, String> violations) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), error, message, request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}
