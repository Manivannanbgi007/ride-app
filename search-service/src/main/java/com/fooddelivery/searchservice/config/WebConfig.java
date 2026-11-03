package com.fooddelivery.searchservice.config;

import java.util.List;

import com.fooddelivery.searchservice.dto.request.FoodSearchCriteria.FoodSort;
import com.fooddelivery.searchservice.dto.request.RestaurantSearchCriteria.RestaurantSort;
import com.fooddelivery.searchservice.web.CallerInfoArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String SEARCH_API = "/api/v1/search";
    public static final String ADMIN_API = "/api/v1/admin";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerInfoArgumentResolver());
    }

    /** "sort=rating" works like "sort=RATING"; every other value is read exactly as before (an unknown one is a 400). */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, FoodSort.class, ratingInAnyCase(FoodSort.class, FoodSort.RATING));
        registry.addConverter(String.class, RestaurantSort.class,
                ratingInAnyCase(RestaurantSort.class, RestaurantSort.RATING));
    }

    private static <E extends Enum<E>> Converter<String, E> ratingInAnyCase(Class<E> type, E rating) {
        return source -> {
            String text = source.trim();
            if (text.isEmpty()) {
                return null;
            }
            return text.equalsIgnoreCase(rating.name()) ? rating : Enum.valueOf(type, text);
        };
    }
}
