package com.fooddelivery.searchservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fooddelivery.searchservice.document.CategoryDocument;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.dto.response.Suggestions;
import com.fooddelivery.searchservice.dto.response.Suggestions.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Search-as-you-type: a few names of each kind that start with what was typed ("chi bir" -> "Chicken Biryani"). */
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final int PER_KIND = 5;

    private final ElasticsearchOperations operations;

    public Suggestions suggest(String text, String city) {
        String typed = text.trim();
        boolean inCity = StringUtils.hasText(city);

        List<Query> restaurantFilters = new ArrayList<>();
        restaurantFilters.add(SearchQueries.exact("status", RestaurantDocument.VISIBLE_STATUS));
        List<Query> foodFilters = new ArrayList<>(FoodSearchService.orderable());
        if (inCity) {
            restaurantFilters.add(SearchQueries.exactIgnoringCase("city", city.trim()));
            foodFilters.add(SearchQueries.exactIgnoringCase("restaurantCity", city.trim()));
        }

        List<Suggestion> restaurants = find(typed, restaurantFilters, RestaurantDocument.class,
                restaurant -> new Suggestion(restaurant.getId(), restaurant.getName(), restaurant.getCity()));
        List<Suggestion> foods = find(typed, foodFilters, FoodDocument.class,
                food -> new Suggestion(food.getId(), food.getName(), food.getRestaurantName()));
        List<Suggestion> categories = find(typed, List.of(SearchQueries.exact("status", "ACTIVE")),
                CategoryDocument.class, category -> new Suggestion(category.getId(), category.getName(), null));
        return new Suggestions(typed, restaurants, foods, categories);
    }

    private <T> List<Suggestion> find(String typed, List<Query> filters, Class<T> type, Function<T, Suggestion> toSuggestion) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters).must(SearchQueries.namePrefix(typed))))
                .withMaxResults(PER_KIND)
                .build();
        return operations.search(query, type).getSearchHits().stream()
                .map(hit -> toSuggestion.apply(hit.getContent()))
                .toList();
    }
}
