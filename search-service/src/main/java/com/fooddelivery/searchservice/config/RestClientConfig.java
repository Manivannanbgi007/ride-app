package com.fooddelivery.searchservice.config;

import java.net.http.HttpClient;

import com.fooddelivery.searchservice.client.RequestIdPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The services that own the data the indexes are built from. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restaurantServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.restaurantService());
    }

    @Bean
    public RestClient catalogServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.catalogService());
    }

    @Bean
    public RestClient reviewServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.review());
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
