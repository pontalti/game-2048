package com.pontalti.game2048.adapter.rest.exception;

import com.pontalti.game2048.adapter.rest.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain and validation errors into clean HTTP responses, so the
 * controller methods stay focused on the happy path. This keeps HTTP concerns
 * (status codes, error bodies) at the boundary, out of the domain.
 */
@RestControllerAdvice
public class GameExceptionHandler {

    /** Unknown game id -> 404 Not Found. */
    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(GameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    /** Invalid body (e.g. missing direction) -> 400 Bad Request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("invalid request body");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", detail));
    }

    /** Malformed JSON or an unknown direction value -> 400 Bad Request. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST",
                        "Malformed request body or invalid direction (use LEFT, RIGHT, UP, DOWN)"));
    }

    /** Domain guard failures (e.g. negative Position) -> 400 Bad Request. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }
}
