package com.fooddelivery.aimcpclient.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatRequest;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatResponse;
import com.fooddelivery.aimcpclient.dto.response.ActionConfirmResponse;
import com.fooddelivery.aimcpclient.dto.response.StatusResponse;
import com.fooddelivery.aimcpclient.exception.AgentCallException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only way to ai-mcp-server. Sends the caller's access token (ai-mcp-server checks it again and calls the platform
 * with it) next to the service token (added by the RestClient),
 * caps the size of answers, and turns every failure into an {@link AgentCallException} with a message that is safe
 * to show to the user.
 * <p>
 * No retries, on purpose: a retried chat repeats expensive LLM work, and a retried confirm could repeat a side
 * effect (the server is idempotent, but we do not rely on it here).
 */
@Slf4j
@Component
public class AgentServerClient {

    static final String MSG_UNAVAILABLE = "The assistant is unavailable right now. Please try again in a moment.";
    static final String MSG_TIMEOUT = "The assistant took too long to answer. Please try again.";
    static final String MSG_BUSY = "The assistant is busy, please wait for your previous question";
    static final String MSG_BAD_ANSWER = "The assistant returned an answer that could not be used. Please try again.";
    private static final int MAX_PROBLEM_BYTES = 4096;
    private static final int MAX_REASON = 200;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}+");
    private static final Pattern TECHNICAL = Pattern.compile(
            "(?i)://|exception|jdbc|\\bsql\\b|\\.java\\b|\\bat [\\w$]+\\.[\\w$.]+\\(|\\binternal\\b");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final AgentCircuitBreaker breaker;
    private final int maxResponseBytes;

    public AgentServerClient(RestClient agentServerRestClient, JsonMapper jsonMapper, Clock clock,
                             AppProperties properties) {
        this.restClient = agentServerRestClient;
        this.jsonMapper = jsonMapper;
        AppProperties.AgentServer config = properties.agentServer();
        this.maxResponseBytes = config.maxResponseBytes();
        this.breaker = new AgentCircuitBreaker(clock, config.breakerFailures(), config.breakerOpenFor());
    }

    public AgentChatResponse chat(CallerIdentity caller, AgentChatRequest request) {
        return call(HttpMethod.POST, "/internal/agent/chat", caller, request, AgentChatResponse.class);
    }

    public ActionConfirmResponse confirm(CallerIdentity caller, String actionId) {
        return call(HttpMethod.POST, "/internal/agent/actions/{id}/confirm", caller, null,
                ActionConfirmResponse.class, actionId);
    }

    public void cancel(CallerIdentity caller, String actionId) {
        call(HttpMethod.POST, "/internal/agent/actions/{id}/cancel", caller, null, Void.class, actionId);
    }

    public StatusResponse status() {
        return call(HttpMethod.GET, "/internal/agent/status", null, null, StatusResponse.class);
    }

    private <T> T call(HttpMethod method, String uri, CallerIdentity caller, Object body, Class<T> type,
                       Object... uriVariables) {
        if (!breaker.allowsCalls()) {
            throw new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
        }
        try {
            T result = send(method, uri, caller, body, type, uriVariables);
            breaker.recordSuccess();
            return result;
        } catch (AgentCallException e) {
            // 4xx answers (not found, conflict, ...) mean the server works: only real failures open the breaker
            if (e.getStatus().is5xxServerError()) {
                breaker.recordFailure();
            } else {
                breaker.recordSuccess();
            }
            throw e;
        }
    }

    private <T> T send(HttpMethod method, String uri, CallerIdentity caller, Object body, Class<T> type,
                       Object... uriVariables) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(uri, uriVariables);
        if (caller != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.accessToken());
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        byte[] bytes;
        try {
            bytes = spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                if (code >= 400) {
                    log.warn("ai-mcp-server answered {} to {} {}", code, method, uri);
                    AgentCallException error = errorFor(code);
                    // A refused change says why (written for the user by the server, for example "the restaurant has
                    // already accepted it"); every other failure keeps the generic message
                    if (code == 409 || code == 422) {
                        String reason = problemDetail(response.getBody().readNBytes(MAX_PROBLEM_BYTES));
                        if (!reason.isEmpty()) {
                            throw new AgentCallException(error.getStatus(), reason);
                        }
                    }
                    throw error;
                }
                byte[] read = response.getBody().readNBytes(maxResponseBytes + 1);
                if (read.length > maxResponseBytes) {
                    log.warn("ai-mcp-server answer to {} {} is larger than {} bytes", method, uri, maxResponseBytes);
                    throw new AgentCallException(HttpStatus.BAD_GATEWAY, MSG_BAD_ANSWER);
                }
                return read;
            });
        } catch (ResourceAccessException e) {
            throw connectionProblem(method, uri, e);
        }
        if (type == Void.class) {
            return null;
        }
        try {
            return jsonMapper.readValue(bytes, type);
        } catch (JacksonException e) {
            log.warn("ai-mcp-server answer to {} {} is not valid: {}", method, uri, e.getOriginalMessage());
            throw new AgentCallException(HttpStatus.BAD_GATEWAY, MSG_BAD_ANSWER);
        }
    }

    private static AgentCallException connectionProblem(HttpMethod method, String uri, ResourceAccessException e) {
        if (isReadTimeout(e)) {
            log.warn("ai-mcp-server did not answer {} {} in time", method, uri);
            return new AgentCallException(HttpStatus.GATEWAY_TIMEOUT, MSG_TIMEOUT);
        }
        log.warn("ai-mcp-server cannot be reached for {} {}: {}", method, uri,
                e.getMostSpecificCause().getClass().getSimpleName());
        return new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
    }

    /** A connect timeout means "not reachable"; only a slow answer is a "took too long". */
    private static boolean isReadTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The "detail" of a problem answer: plain text, without control characters, at most 200 characters; "" when it is
     * missing or looks technical (an address, an exception, SQL), so nothing internal ever reaches the browser.
     */
    private String problemDetail(byte[] body) {
        try {
            String detail = jsonMapper.readTree(body).path("detail").asString("");
            String plain = CONTROL.matcher(detail).replaceAll(" ").strip();
            if (TECHNICAL.matcher(plain).find()) {
                return "";
            }
            return plain.length() > MAX_REASON ? plain.substring(0, MAX_REASON) : plain;
        } catch (JacksonException e) {
            return "";
        }
    }

    /** Statuses the browser may see; everything else about the server stays internal. */
    private static AgentCallException errorFor(int code) {
        return switch (code) {
            case 400 -> new AgentCallException(HttpStatus.BAD_REQUEST, "The assistant could not accept this request.");
            case 403 -> new AgentCallException(HttpStatus.FORBIDDEN, "You are not allowed to do that.");
            case 404 -> new AgentCallException(HttpStatus.NOT_FOUND, "This action was not found or has expired.");
            case 409 -> new AgentCallException(HttpStatus.CONFLICT,
                    "This action can no longer be completed because something changed. Please ask again.");
            case 410 -> new AgentCallException(HttpStatus.GONE, "This action has expired. Please ask again.");
            case 422 -> new AgentCallException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The platform refused this change. Please ask again.");
            case 429 -> new AgentCallException(HttpStatus.TOO_MANY_REQUESTS, MSG_BUSY);
            case 504 -> new AgentCallException(HttpStatus.GATEWAY_TIMEOUT, MSG_TIMEOUT);
            // 401/5xx: the server is down, restarting or misconfigured (token): the user can only retry later
            case 401, 500, 502, 503 -> new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
            default -> new AgentCallException(HttpStatus.BAD_GATEWAY, MSG_BAD_ANSWER);
        };
    }
}
