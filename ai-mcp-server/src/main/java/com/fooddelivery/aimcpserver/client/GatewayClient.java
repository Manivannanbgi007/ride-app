package com.fooddelivery.aimcpserver.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.ToolText;
import com.fooddelivery.aimcpserver.web.ServiceHeaders;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one and only way this service reaches the business APIs: through the API Gateway, as the caller (their access
 * token is forwarded, so every service applies its own permission and ownership checks). Paths are fixed strings in
 * the tools; only numeric ids and encoded query values are added to them.
 * <p>
 * Timeouts come from the RestClient; idempotent GETs are tried once more when the gateway is briefly unreachable;
 * a circuit breaker stops calling a gateway that keeps failing, so the assistant answers at once instead of waiting.
 */
@Slf4j
@Component
public class GatewayClient {

    private static final long RETRY_PAUSE_MILLIS = 150;
    private static final int MAX_ERROR_DETAIL = 200;
    private static final String RETRY_LATER = " Please try again in a moment.";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final CircuitBreaker circuitBreaker;

    public GatewayClient(@Qualifier("gatewayRestClient") RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.circuitBreaker = CircuitBreaker.of("gateway", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .build());
    }

    public JsonNode get(CallerContext caller, String path, Map<String, ?> query) {
        try {
            return send(caller, HttpMethod.GET, path, query, null, null);
        } catch (GatewayException first) {
            if (!isBriefOutage(first)) {
                throw first;
            }
            pause();
            return send(caller, HttpMethod.GET, path, query, null, null);
        }
    }

    public JsonNode put(CallerContext caller, String path, Object body) {
        return send(caller, HttpMethod.PUT, path, Map.of(), body, null);
    }

    public JsonNode post(CallerContext caller, String path, Object body, String idempotencyKey) {
        return send(caller, HttpMethod.POST, path, Map.of(), body, idempotencyKey);
    }

    public void delete(CallerContext caller, String path) {
        send(caller, HttpMethod.DELETE, path, Map.of(), null, null);
    }

    private JsonNode send(CallerContext caller, HttpMethod method, String path, Map<String, ?> query, Object body,
                          String idempotencyKey) {
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException e) {
            throw new GatewayException(0, true, false, "The platform is not answering right now." + RETRY_LATER);
        }
        long start = System.nanoTime();
        try {
            JsonNode result = call(caller, method, path, query, body, idempotencyKey);
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (GatewayException e) {
            // Only failures of the platform trip the breaker; a 404 or a refused coupon is a normal answer
            if (e.isTransientFailure()) {
                circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            } else {
                circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            }
            throw e;
        }
    }

    private JsonNode call(CallerContext caller, HttpMethod method, String path, Map<String, ?> query, Object body,
                          String idempotencyKey) {
        Map<String, Object> vars = new LinkedHashMap<>();
        query.forEach((key, value) -> {
            if (value != null) {
                vars.put(key, value);
            }
        });
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        // Placeholders: the values are encoded strictly, so "a&status=X" cannot add a parameter
                        vars.keySet().forEach(key -> uriBuilder.queryParam(key, "{" + key + "}"));
                        return uriBuilder.build(vars);
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> identity(headers, caller, idempotencyKey));
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            JsonNode result = spec.retrieve().body(JsonNode.class);
            return result == null ? jsonMapper.createObjectNode() : result;
        } catch (RestClientResponseException e) {
            throw fromResponse(e);
        } catch (ResourceAccessException e) {
            throw fromIoFailure(e);
        }
    }

    private static void identity(HttpHeaders headers, CallerContext caller, String idempotencyKey) {
        // The platform knows the caller from their own access token only (the gateway and each service check it)
        if (caller.accessToken() != null) {
            headers.setBearerAuth(caller.accessToken());
        }
        if (caller.requestId() != null) {
            headers.set(ServiceHeaders.REQUEST_ID, caller.requestId());
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
    }

    private GatewayException fromResponse(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detail = problemDetail(e.getResponseBodyAsString());
        log.debug("Gateway answered status={} detail={}", status, detail);
        if (status == 401 || status == 403) {
            return new GatewayException(status, false, false, "The platform says this is not allowed for you.");
        }
        if (status == 404) {
            return new GatewayException(status, false, false, "Not found" + (detail.isEmpty() ? "." : ": " + detail));
        }
        if (status == 429) {
            return new GatewayException(status, true, false, "Too many requests right now." + RETRY_LATER);
        }
        if (status >= 500) {
            return new GatewayException(status, true, false, "The platform is temporarily unavailable." + RETRY_LATER);
        }
        return new GatewayException(status, false, false, detail.isEmpty() ? "The request was refused." : detail);
    }

    private static GatewayException fromIoFailure(ResourceAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
            return new GatewayException(0, true, true, "The platform took too long to answer." + RETRY_LATER);
        }
        log.warn("Gateway call failed: {}", cause instanceof ConnectException ? "connection refused" : "io error");
        return new GatewayException(0, true, false, "The platform is not reachable right now." + RETRY_LATER);
    }

    // The gateway and the services answer with RFC 9457 problem details: "detail" is the human message
    private String problemDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode json = jsonMapper.readTree(body);
            for (String field : new String[] {"detail", "message", "title"}) {
                String text = json.path(field).asString("");
                if (!text.isBlank()) {
                    return ToolText.clean(text, MAX_ERROR_DETAIL);
                }
            }
        } catch (JacksonException e) {
            log.debug("Error body is not JSON");
        }
        return "";
    }

    private static boolean isBriefOutage(GatewayException e) {
        return e.isTransientFailure() && !e.isTimedOut()
                && (e.getStatus() == 0 || e.getStatus() == 502 || e.getStatus() == 503 || e.getStatus() == 504);
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
