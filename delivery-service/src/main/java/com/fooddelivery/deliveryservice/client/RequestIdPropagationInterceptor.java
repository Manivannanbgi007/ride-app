package com.fooddelivery.deliveryservice.client;

import java.io.IOException;

import com.fooddelivery.deliveryservice.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Passes the current X-Request-Id on to the service being called,
 * so one business request can be followed across service logs.
 */
public class RequestIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            request.getHeaders().set(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
        return execution.execute(request, body);
    }
}
