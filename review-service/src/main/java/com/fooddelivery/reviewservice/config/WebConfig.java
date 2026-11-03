package com.fooddelivery.reviewservice.config;

import java.util.List;

import com.fooddelivery.reviewservice.web.CallerInfoArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String CUSTOMER_API = "/api/v1/reviews";
    public static final String ADMIN_API = "/api/v1/admin/reviews";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerInfoArgumentResolver());
    }
}
