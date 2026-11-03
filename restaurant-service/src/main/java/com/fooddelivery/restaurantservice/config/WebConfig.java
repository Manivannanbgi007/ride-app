package com.fooddelivery.restaurantservice.config;

import java.util.List;

import com.fooddelivery.restaurantservice.web.CallerInfoArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String OWNER_API = "/api/v1/restaurants/mine";
    public static final String ADMIN_API = "/api/v1/admin";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerInfoArgumentResolver());
    }
}
