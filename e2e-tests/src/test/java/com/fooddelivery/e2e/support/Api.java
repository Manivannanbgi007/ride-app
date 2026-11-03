package com.fooddelivery.e2e.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * The platform's REST API, as a client sees it: {@code Api.as(customer).header("Idempotency-Key", key).post(...)}.
 * Every call goes through the API Gateway.
 */
public final class Api {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private Api() {
    }

    public static Request as(Actor actor) {
        return new Request(actor);
    }

    /** Without an identity: registration, sign-in sessions, public pages. */
    public static Request anonymous() {
        return new Request(null);
    }

    /** One request being put together. */
    public static final class Request {

        private final Map<String, String> headers = new LinkedHashMap<>();

        private Request(Actor actor) {
            if (actor != null) {
                // The token alone says who calls (the gateway and every service check it)
                headers.put("Authorization", "Bearer " + actor.accessToken());
            }
        }

        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Response get(String path) {
            return send("GET", path, null);
        }

        public Response post(String path, Object body) {
            return send("POST", path, body);
        }

        public Response post(String path) {
            return send("POST", path, null);
        }

        public Response put(String path, Object body) {
            return send("PUT", path, body);
        }

        public Response put(String path) {
            return send("PUT", path, null);
        }

        public Response delete(String path) {
            return send("DELETE", path, null);
        }

        private Response send(String method, String path, Object body) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(E2EConfig.baseUrl() + path)).timeout(TIMEOUT);
            headers.forEach(request::header);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            try {
                HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.headers(), parse(response.body()), response.body());
            } catch (IOException e) {
                throw new IllegalStateException(method + " " + path + " failed: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(method + " " + path + " was interrupted", e);
            }
        }
    }

    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            return MissingNode.getInstance(); // not JSON (for example a plain-text error page)
        }
    }
}
