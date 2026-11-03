package com.fooddelivery.aimcpserver.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fooddelivery.aimcpserver.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the model into Ollama's memory right after startup, in the background. On a CPU, loading takes about two
 * minutes, so without this the first question after a restart would pay for it and could run out of time.
 * Ollama keeps the model loaded afterwards (its keep_alive). It never blocks startup and never fails the service:
 * Ollama may simply not be up yet (it is retried), or the model may not be installed yet (then the status
 * endpoint says so).
 */
@Slf4j
@Component
public class ModelWarmUp {

    private static final int ATTEMPTS = 12;
    private static final Duration PAUSE = Duration.ofSeconds(10);
    private static final Duration LOAD_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final URI generateUri;
    private final String model;
    private final JsonMapper jsonMapper;
    private final boolean enabled;

    public ModelWarmUp(AppProperties properties, JsonMapper jsonMapper,
                       @Value("${app.ollama.warm-up:true}") boolean enabled) {
        AppProperties.Ollama ollama = properties.ollama();
        this.httpClient = HttpClient.newBuilder().connectTimeout(ollama.statusTimeout()).build();
        String base = ollama.baseUrl().endsWith("/") ? ollama.baseUrl() : ollama.baseUrl() + "/";
        this.generateUri = URI.create(base + "api/generate");
        this.model = ollama.model();
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpInBackground() {
        if (enabled) {
            Thread.ofVirtual().name("model-warm-up").start(this::warmUp);
        }
    }

    /** Tries up to ATTEMPTS times; returns whether the model was loaded. Public for the tests. */
    public boolean warmUp() {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (loadOnce()) {
                log.info("Model {} is loaded in Ollama (attempt {})", model, attempt);
                return true;
            }
            try {
                Thread.sleep(PAUSE.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("Model {} could not be loaded in advance; the first question will load it", model);
        return false;
    }

    /** An empty generate request with keep_alive loads the model without asking it anything. */
    boolean loadOnce() {
        String body = jsonMapper.createObjectNode().put("model", model).put("keep_alive", "30m").put("stream", false)
                .toString();
        HttpRequest request = HttpRequest.newBuilder(generateUri).timeout(LOAD_TIMEOUT)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            log.debug("Model warm-up attempt failed: {}", e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
