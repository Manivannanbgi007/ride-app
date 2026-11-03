package com.fooddelivery.aimcpserver.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** MOCKED: a local stub stands in for Ollama; what matters is the request that loads the model. */
class ModelWarmUpTest {

    private HttpServer ollama;
    private final List<String> bodies = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);

    @BeforeEach
    void startStub() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollama.createContext("/api/generate", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        ollama.start();
    }

    @AfterEach
    void stopStub() {
        ollama.stop(0);
    }

    private ModelWarmUp warmUp(boolean enabled) {
        AppProperties properties = Fixtures.properties("http://127.0.0.1:1");
        AppProperties.Ollama stub = new AppProperties.Ollama("http://127.0.0.1:" + ollama.getAddress().getPort(),
                "qwen-test", properties.ollama().requestTimeout(), properties.ollama().statusTimeout());
        return new ModelWarmUp(new AppProperties(properties.serviceToken(), properties.timezone(), properties.gateway(),
                stub, properties.agent(), properties.proposal()), JsonMapper.builder().build(), enabled);
    }

    @Test
    void mockedItAsksOllamaToLoadTheModelAndKeepItLoaded() {
        boolean loaded = warmUp(true).loadOnce();

        assertThat(loaded).isTrue();
        assertThat(bodies).singleElement().satisfies(body -> assertThat(body)
                .contains("\"model\":\"qwen-test\"").contains("\"keep_alive\":\"30m\"").contains("\"stream\":false")
                .doesNotContain("prompt"));
    }

    @Test
    void mockedAnErrorAnswerOrNobodyListeningIsNotLoadedAndNeverThrows() {
        status.set(500);
        assertThat(warmUp(true).loadOnce()).isFalse();

        ollama.stop(0);
        assertThat(warmUp(true).loadOnce()).isFalse();
    }

    @Test
    void mockedWhenSwitchedOffNothingIsSent() {
        warmUp(false).warmUpInBackground();

        assertThat(bodies).isEmpty();
    }
}
