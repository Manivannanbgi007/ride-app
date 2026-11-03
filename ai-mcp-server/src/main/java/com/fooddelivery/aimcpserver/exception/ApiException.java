package com.fooddelivery.aimcpserver.exception;

import org.springframework.http.HttpStatus;

/** A failure with the HTTP status and the short title the caller should see (turned into a ProblemDetail). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Resource not found", detail);
    }

    public static ApiException gone(String detail) {
        return new ApiException(HttpStatus.GONE, "Proposal expired", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Business conflict", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    public static ApiException unavailable(String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant unavailable", detail);
    }
}
