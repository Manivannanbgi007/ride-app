package com.fooddelivery.aimcpserver.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.aimcpserver.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 ProblemDetail responses (the same shape as the other services), with the requestId on
 * every error. A stack trace never reaches the caller.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception, WebRequest request) {
        return problem(exception.getStatus(), exception.getTitle(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Quote the requestId when contacting support.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return validationProblem(exception, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors()
                        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                result.getResolvableErrors().forEach(error -> errors.putIfAbsent(
                        String.valueOf(result.getMethodParameter().getParameterName()), error.getDefaultMessage()));
            }
        }
        return validationProblem(exception, errors, headers, status, request);
    }

    private <E extends Exception & ErrorResponse> ResponseEntity<Object> validationProblem(
            E exception, Map<String, String> errors, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Every error response (ours and Spring's) passes through here, so the requestId is added once. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (body instanceof ProblemDetail problem && requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return createResponseEntity(problem, new HttpHeaders(), status, request);
    }
}
