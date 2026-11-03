package com.fooddelivery.adminservice.client;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Adds the platform's shared service token (X-Service-Token) to calls of another service's internal API
 * (/api/v1/internal/**), and only to those: the secret never travels with any other call.
 */
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Service-Token";
    private static final String INTERNAL_API = "/api/v1/internal/";

    private final String token;

    public ServiceTokenInterceptor(String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestPath = request.getURI().getPath();
        if (requestPath != null && requestPath.startsWith(INTERNAL_API)) {
            request.getHeaders().set(HEADER, token);
        }
        return execution.execute(request, body);
    }
}
