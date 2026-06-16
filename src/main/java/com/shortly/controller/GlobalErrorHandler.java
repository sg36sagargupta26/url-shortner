package com.shortly.controller;

import com.shortly.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * Global exception handler for translating exceptions into structured
 * {@link ErrorResponse} JSON bodies with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /**
     * Handles Bean Validation errors thrown when {@code @Valid} request bodies fail.
     *
     * @param ex the validation exception
     * @return HTTP 400 with the first validation error message
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validationError(message));
    }

    /**
     * Handles illegal argument exceptions (e.g. invalid TTL format).
     *
     * @param ex the exception
     * @return HTTP 400 with the error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("bad_request", ex.getMessage()));
    }

    /**
     * Catch-all handler for unexpected errors.
     *
     * @param ex the exception
     * @return HTTP 500 with a generic message (details are logged server-side only)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternal(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal_error", "An unexpected error occurred."));
    }
}
