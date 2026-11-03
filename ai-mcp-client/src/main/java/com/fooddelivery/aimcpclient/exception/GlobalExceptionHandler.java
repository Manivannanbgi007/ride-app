package com.fooddelivery.aimcpclient.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.aimcpclient.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 ProblemDetail responses (same shape as the other services, with a "requestId").
 * Messages are written for the chat user: never internal URLs, hosts or stack traces.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationFailedException exception, WebRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException exception, WebRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequests(TooManyRequestsException exception, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Too many requests");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, exception.getRetryAfterSeconds())));
        return createResponseEntity(problem, headers, HttpStatus.TOO_MANY_REQUESTS, request);
    }

    @ExceptionHandler(AgentCallException.class)
    public ResponseEntity<Object> handleAgentCall(AgentCallException exception, WebRequest request) {
        return problem(exception.getStatus(), exception.getStatus().getReasonPhrase(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Quote the requestId when contacting support.", request);
    }

    /** Bean Validation errors: return every invalid field with its message. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = exception.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Every error response (ours and Spring's) passes through here, so the requestId is added once. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body,
                                                          HttpHeaders headers,
                                                          HttpStatusCode statusCode,
                                                          WebRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (body instanceof ProblemDetail problem && requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(HttpStatusCode status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return createResponseEntity(problem, new HttpHeaders(), status, request);
    }
}
