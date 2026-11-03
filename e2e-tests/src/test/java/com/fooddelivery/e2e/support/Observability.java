package com.fooddelivery.e2e.support;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import tools.jackson.databind.JsonNode;

/** The development stack's observability tools: Prometheus (metrics), Loki (logs) and Tempo (traces). */
public final class Observability {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Observability() {
    }

    /** The value of a PromQL expression with one result (0 when it has none yet). */
    public static double prometheus(String promQl) {
        JsonNode result = get(E2EConfig.prometheusUrl() + "/api/v1/query?query=" + encode(promQl)).path("data").path("result");
        return result.isEmpty() ? 0 : Double.parseDouble(result.get(0).path("value").get(1).asString());
    }

    /**
     * The value of a PromQL expression over one service's metrics, from a scrape made after the given moment (all of a
     * service's metrics are scraped together, at the time of its up sample).
     */
    public static double scrapedAfter(String service, String promQl, Instant moment) {
        String scrapedAt = "max(timestamp(up{job=\"services\", instance=\"" + service + "\"}))";
        await("a scrape of " + service + " after " + moment).atMost(Duration.ofSeconds(60))
                .until(() -> prometheus(scrapedAt) > moment.getEpochSecond());
        return prometheus(promQl);
    }

    /** The trace id of a service's log lines with this request id (Loki keeps them as structured metadata). */
    public static Optional<String> traceIdOfRequest(String service, String requestId) {
        long end = Instant.now().toEpochMilli() * 1_000_000;
        long start = end - Duration.ofMinutes(15).toNanos();
        String query = "{service=\"" + service + "\"} | request_id=\"" + requestId + "\"";
        JsonNode streams = get(E2EConfig.lokiUrl() + "/loki/api/v1/query_range?query=" + encode(query)
                + "&start=" + start + "&end=" + end + "&limit=50").path("data").path("result");
        return streams.valueStream()
                .map(stream -> stream.path("stream").path("trace_id").asString(""))
                .filter(id -> !id.isEmpty())
                .findFirst();
    }

    /** The services that took part in a trace, as Tempo has it. */
    public static Set<String> servicesInTrace(String traceId) {
        JsonNode trace = get(E2EConfig.tempoUrl() + "/api/traces/" + traceId);
        Set<String> services = new TreeSet<>();
        trace.path("batches").valueStream().forEach(batch -> batch.path("resource").path("attributes").valueStream()
                .filter(attribute -> "service.name".equals(attribute.path("key").asString()))
                .forEach(attribute -> services.add(attribute.path("value").path("stringValue").asString())));
        return services;
    }

    private static JsonNode get(String url) {
        try {
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return Api.parse(response.statusCode() == 200 ? response.body() : "");
        } catch (IOException e) {
            throw new IllegalStateException("Not reachable: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + url, e);
        }
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
