package com.fooddelivery.searchservice.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.searchservice.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into consistent RFC 9457 ProblemDetail responses.
 * <p>
 * Spring MVC's own exceptions (bad JSON, missing parameters, ...) are handled by the parent class;
 * this class adds the business exceptions and a "requestId" field on every error.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationFailedException exception, WebRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException exception, WebRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage(), request);
    }

    /** Elasticsearch cannot be reached: the index is temporarily unavailable, MySQL data is not affected. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Object> handleSearchUnavailable(DataAccessResourceFailureException exception, WebRequest request) {
        log.warn("Elasticsearch unavailable: {}", exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search unavailable",
                "Search is temporarily unavailable. Try again in a moment.", request);
    }

    /** The owning service cannot be reached: the consistency check and repair stop, and nothing is changed. */
    @ExceptionHandler(SourceUnavailableException.class)
    public ResponseEntity<Object> handleSourceUnavailable(SourceUnavailableException exception, WebRequest request) {
        log.warn("Source of truth unavailable: {}", exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Source unavailable",
                exception.getMessage() + ". Nothing was changed; try again when it is back.", request);
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
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = exception.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * Validation of method parameters, for example a header with @Pattern. When a controller method has
     * such constraints, Spring validates the whole call at once (including the @Valid request body),
     * so body field errors arrive here too.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors()
                        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
                bodyErrors.getGlobalErrors()
                        .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> errors.putIfAbsent(name, error.getDefaultMessage()));
            }
        }

        ProblemDetail problem = exception.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** The name the client used: the header or query parameter name, else the Java parameter name. */
    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.value().isEmpty()) {
            return param.value();
        }
        return parameter.getParameterName();
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

    private ResponseEntity<Object> problem(HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return createResponseEntity(problem, new HttpHeaders(), status, request);
    }
}
