package com.fooddelivery.aimcpclient.config;

import java.net.http.HttpClient;
import java.time.Duration;

import com.fooddelivery.aimcpclient.client.RequestIdPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The service this one calls (the caller's access token is added per call, by AgentServerClient). */
@Configuration
public class RestClientConfig {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    @Bean
    public RestClient agentServerRestClient(RestClient.Builder builder, AppProperties properties) {
        AppProperties.AgentServer server = properties.agentServer();
        return build(builder, server.baseUrl(), server.connectTimeout(), server.readTimeout())
                .mutate()
                .defaultHeader(SERVICE_TOKEN_HEADER, server.serviceToken())
                .build();
    }

    private static RestClient build(RestClient.Builder builder, String baseUrl, Duration connect, Duration read) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(read);
        // clone(): each client gets its own copy of the shared, auto-configured builder
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .build();
    }
}
