package com.fooddelivery.aimcpserver.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.dto.StatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Asks Ollama (GET /api/tags, short timeout) whether it runs and has the configured model installed. */
@Slf4j
@Component
public class OllamaStatusClient {

    private final HttpClient httpClient;
    private final URI tagsUri;
    private final String model;
    private final Duration timeout;
    private final JsonMapper jsonMapper;

    public OllamaStatusClient(AppProperties properties, JsonMapper jsonMapper) {
        AppProperties.Ollama ollama = properties.ollama();
        this.httpClient = HttpClient.newBuilder().connectTimeout(ollama.statusTimeout()).build();
        String base = ollama.baseUrl().endsWith("/") ? ollama.baseUrl() : ollama.baseUrl() + "/";
        this.tagsUri = URI.create(base + "api/tags");
        this.model = ollama.model();
        this.timeout = ollama.statusTimeout();
        this.jsonMapper = jsonMapper;
    }

    public StatusResponse check() {
        HttpRequest request = HttpRequest.newBuilder(tagsUri).timeout(timeout).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new StatusResponse(false, model, "The AI service answered with an error. Please try again later.");
            }
            List<String> installed = new ArrayList<>();
            for (JsonNode entry : jsonMapper.readTree(response.body()).path("models")) {
                installed.add(entry.path("name").asString(""));
            }
            boolean present = installed.stream().anyMatch(name -> name.equals(model)
                    || (!model.contains(":") && name.equals(model + ":latest")));
            if (present) {
                return new StatusResponse(true, model, null);
            }
            return new StatusResponse(false, model,
                    "The AI model " + model + " is not installed yet (it may still be downloading).");
        } catch (IOException | JacksonException e) {
            log.debug("Ollama status check failed: {}", e.getClass().getSimpleName());
            return new StatusResponse(false, model, "The AI service is not running right now.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StatusResponse(false, model, "The status check was interrupted.");
        }
    }
}
