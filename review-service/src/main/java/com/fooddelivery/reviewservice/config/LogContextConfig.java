package com.fooddelivery.reviewservice.config;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The log lines of a request about an order carry its id (orderId, taken
 * from the URL path, e.g. /api/v1/reviews/orders/{orderId}), so Loki can find everything that happened to it.
 */
@Configuration
public class LogContextConfig implements WebMvcConfigurer {

    static final List<String> PATH_IDS = List.of("orderId");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
                    for (String id : PATH_IDS) {
                        if (variables.get(id) instanceof String value) {
                            MDC.put(id, value);
                        }
                    }
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                        Exception exception) {
                PATH_IDS.forEach(MDC::remove);
            }
        });
    }
}
