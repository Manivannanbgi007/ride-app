package com.fooddelivery.aimcpserver.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The two HTTP clients of this service: the gateway (business APIs) and the Ollama model API. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gatewayRestClient(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Gateway gateway = properties.gateway();
        return withTimeouts(builder, gateway.connectTimeout(), gateway.readTimeout())
                .baseUrl(gateway.baseUrl())
                .build();
    }

    /** Spring AI's Ollama client with our timeout: the model is slow on a CPU, the default would cut it off. */
    @Bean
    public OllamaApi ollamaApi(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Ollama ollama = properties.ollama();
        return OllamaApi.builder()
                .baseUrl(ollama.baseUrl())
                .restClientBuilder(withTimeouts(builder, Duration.ofSeconds(3), ollama.requestTimeout()))
                .build();
    }

    // clone(): each client gets its own copy of the shared, auto-configured builder
    private static RestClient.Builder withTimeouts(RestClient.Builder builder, Duration connect, Duration read) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(read);
        return builder.clone().requestFactory(requestFactory);
    }
}
