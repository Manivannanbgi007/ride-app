package com.fooddelivery.orderservice.config;

import java.net.http.HttpClient;

import com.fooddelivery.orderservice.client.RequestIdPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** One RestClient per remote service, each with its own base URL and timeouts. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient userServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.services().user());
    }

    @Bean
    public RestClient restaurantServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.services().restaurant());
    }

    @Bean
    public RestClient catalogServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.services().catalog());
    }

    @Bean
    public RestClient promotionServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        return build(builder, appProperties.services().promotion());
    }

    private static RestClient build(RestClient.Builder builder, AppProperties.RemoteService service) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(service.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(service.readTimeout());

        return builder
                .baseUrl(service.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .build();
    }
}
