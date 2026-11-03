package com.fooddelivery.chatservice.config;

import java.net.http.HttpClient;

import com.fooddelivery.chatservice.client.RequestIdPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The two services this one calls. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient userServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.userService());
    }

    @Bean
    public RestClient notificationServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.notificationService());
    }

    private static RestClient build(RestClient.Builder builder, AppProperties.RemoteService service) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(service.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(service.readTimeout());
        // clone(): each client gets its own copy of the shared, auto-configured builder
        return builder.clone()
                .baseUrl(service.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .build();
    }
}
