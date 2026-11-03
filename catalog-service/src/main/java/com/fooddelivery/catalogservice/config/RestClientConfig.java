package com.fooddelivery.catalogservice.config;

import java.net.http.HttpClient;

import com.fooddelivery.catalogservice.client.RequestIdPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restaurantServiceRestClient(RestClient.Builder builder, AppProperties appProperties) {
        AppProperties.RestaurantService restaurantService = appProperties.restaurantService();

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(restaurantService.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(restaurantService.readTimeout());

        return builder
                .baseUrl(restaurantService.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .build();
    }
}
