package com.fooddelivery.deliverypartnerservice.config;

import java.util.List;

import com.fooddelivery.deliverypartnerservice.web.CallerInfoArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Who may call what. The internal API (/api/v1/internal/**) is for other services only: the API Gateway has no
 * route for it.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String PARTNER_API = "/api/v1/delivery/partners";
    public static final String ADMIN_API = "/api/v1/admin";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerInfoArgumentResolver());
    }
}
