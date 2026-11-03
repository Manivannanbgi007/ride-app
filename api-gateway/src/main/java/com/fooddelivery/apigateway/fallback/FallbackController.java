package com.fooddelivery.apigateway.fallback;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * What the client gets when a service cannot answer (the routes' CircuitBreaker filters forward here): the same
 * ProblemDetail shape as the services' own errors, 503 (or 504 when it did not answer in time), and a hint when
 * to try again. Without a circuit, a client would wait for every connection attempt to a stopped service.
 */
@RestController
public class FallbackController {

    static final String RETRY_AFTER_SECONDS = "10";

    // The circuit breaker forwards the request as it came in, so the fallback answers every method the routes carry
    @RequestMapping(path = "/fallback/{service}", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ResponseEntity<ProblemDetail>> serviceUnavailable(@PathVariable String service,
                                                                  ServerWebExchange exchange) {
        Throwable failure = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        boolean timedOut = timedOut(failure);
        HttpStatus status = timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, timedOut
                ? service + " did not answer in time. Try again later."
                : service + " is not available right now. Try again in a few seconds.");
        problem.setTitle(timedOut ? "Service timeout" : "Service unavailable");
        problem.setProperty("service", service);
        Set<URI> originalUrls = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUrls != null && !originalUrls.isEmpty()) {
            problem.setInstance(URI.create(originalUrls.iterator().next().getPath()));
        }
        return Mono.just(ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem));
    }

    /**
     * The route's response-timeout (the gateway reports it as 504), or the circuit breaker's overall limit
     * (app.gateway.response-timeout-limit, a TimeoutException).
     */
    private static boolean timedOut(Throwable failure) {
        return failure instanceof TimeoutException
                || failure instanceof ResponseStatusException e && HttpStatus.GATEWAY_TIMEOUT.equals(e.getStatusCode());
    }
}
