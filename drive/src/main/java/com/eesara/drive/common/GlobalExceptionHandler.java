package com.eesara.drive.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> api(ApiException exception) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = fields.values().stream().findFirst().orElse("Please check the submitted information.");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, fields);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> badCredentials(BadCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INCORRECT_PASSWORD",
                "The password you entered is incorrect.", Map.of());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> disabled(DisabledException exception) {
        return response(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                "This account has been suspended. Contact an administrator.", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, "REQUEST_FAILED",
                exception.getReason() == null ? "The request could not be completed." : exception.getReason(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR",
                "The server could not complete the request. Please try again later.", Map.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                false, Instant.now(), status.value(), code, message, fields));
    }
}
