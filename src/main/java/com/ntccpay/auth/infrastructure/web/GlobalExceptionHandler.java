package com.ntccpay.auth.infrastructure.web;

import com.ntccpay.auth.application.exception.IdempotencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** problem+json error responses, consistent with RFC 9457. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Idempotency key conflict");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validationFailed(MethodArgumentNotValidException e) {
        var message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return invalidRequestProblem(message);
    }

    /**
     * Raised when any handler parameter carries a constraint (e.g. @NotBlank on a
     * header): request-body validation failures are then reported through the
     * unified method-validation path instead of MethodArgumentNotValidException.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail methodValidationFailed(HandlerMethodValidationException e) {
        var message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return invalidRequestProblem(message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail missingHeader(MissingRequestHeaderException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "required header '" + e.getHeaderName() + "' is missing");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badValue(IllegalArgumentException e) {
        return invalidRequestProblem(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception while processing request", e);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "unexpected error; the incident has been logged");
        problem.setTitle("Internal error");
        return problem;
    }

    private static ProblemDetail invalidRequestProblem(String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        return problem;
    }
}
